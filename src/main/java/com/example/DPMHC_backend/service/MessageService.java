package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.dto.cache.PageCacheWrapper;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository participantRepository;
    private final MessageReactionRepository reactionRepository;
    private final MessageReadStatusRepository readStatusRepository;
    private final UserRepository userRepository;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final com.example.DPMHC_backend.service.WebSocketService webSocketService;
    private final UserBlockRepository userBlockRepository;

    /**
     * Send a new message
     */
    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "chat-messages", key = "#request.chatId + ':*'", allEntries = false),
        @CacheEvict(value = "chat-lists", key = "#userId + ':*'", allEntries = false),
        @CacheEvict(value = "message-counts", key = "#request.chatId")
    })
    public MessageDTO sendMessage(MessageSendRequestDTO request, Long userId) {
        // Debug logging to see what's being received
        log.info("Received message send request: {}", request);
        log.info("Sending message - Content: {}, MessageType: {}, MediaUrl: {}",
                request.getContent(), request.getMessageType(), request.getMediaUrl());

        // Enhanced validation that handles media messages properly
        if (request.getMessageType() == null) {
            throw new RuntimeException("Message type is required");
        }

        // For text/emoji messages, content is required
        if (request.getMessageType() == Message.MessageType.TEXT ||
                request.getMessageType() == Message.MessageType.EMOJI) {
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new RuntimeException("Content is required for text messages");
            }
        }

        // For media messages, mediaUrl is required
        if (request.getMessageType() == Message.MessageType.IMAGE ||
                request.getMessageType() == Message.MessageType.VIDEO ||
                request.getMessageType() == Message.MessageType.AUDIO ||
                request.getMessageType() == Message.MessageType.DOCUMENT) {
            if (request.getMediaUrl() == null || request.getMediaUrl().trim().isEmpty()) {
                throw new RuntimeException("Media URL is required for media messages");
            }
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        Chat chat = chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Check if user is participant of the chat using existing repository
        if (!chatRepository.isUserParticipantOfChat(request.getChatId(), userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        // Check for blocked users - prevent messaging between blocked users
        if (chat.getChatType() == Chat.ChatType.PRIVATE) {
            // For private chats, check if any participants have blocked each other
            List<ChatParticipant> participants = chat.getParticipants();
            for (ChatParticipant participant : participants) {
                if (!participant.getUser().getId().equals(userId)) {
                    // Check if sender is blocked by the other participant or vice versa
                    if (userBlockRepository.areUsersBlocked(userId, participant.getUser().getId())) {
                        throw new RuntimeException("Cannot send message: Users are blocked");
                    }
                }
            }
        }

        // Handle reply validation
        Message replyToMessage = null;
        if (request.getReplyToId() != null) {
            replyToMessage = messageRepository.findById(request.getReplyToId())
                    .orElseThrow(() -> new RuntimeException("Reply message not found"));

            // Ensure the reply message belongs to the same chat
            if (!replyToMessage.getChat().getId().equals(request.getChatId())) {
                throw new RuntimeException("Cannot reply to message from different chat");
            }
        }

        // Create the message
        Message message = Message.builder()
                .content(request.getContent()) // This can be null for media messages
                .messageType(request.getMessageType())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .mediaSize(request.getMediaSize())
                .thumbnailUrl(request.getThumbnailUrl())
                .isEdited(false)
                .isDeleted(false)
                .isPinned(false)
                .sender(sender)
                .chat(chat)
                .replyToId(replyToMessage != null ? replyToMessage.getId() : null)
                .build();

        Message savedMessage = messageRepository.save(message);
        log.info("DEBUG: Saved message with ID: {}, MediaUrl: {}, MessageType: {}",
                savedMessage.getId(), savedMessage.getMediaUrl(), savedMessage.getMessageType());

        // Update chat's last message timestamp
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        // Create read statuses for all participants
        createReadStatusForAllParticipants(savedMessage);

        // Broadcast message via WebSocket
        MessageDTO messageDTO = new MessageDTO(savedMessage);
        webSocketService.broadcastNewMessage(messageDTO);

        return messageDTO;
    }

    /**
     * Get messages in a chat with pagination
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Page<MessageDTO> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        log.debug("💬 Fetching chat messages for chat {} by user {} (page {}, size {})", 
                  chatId, userId, pageable.getPageNumber(), pageable.getPageSize());
        
        DatabaseContextHolder.setUserContext(userId.toString());
        
        // Try to get from cache first using wrapper
        PageCacheWrapper<MessageDTO> cachedWrapper = getCachedMessages(chatId, userId, pageable);
        if (cachedWrapper != null) {
            log.debug("🎯 Cache HIT: Retrieved {} messages from cache for chat {}", 
                     cachedWrapper.getContent().size(), chatId);
            return cachedWrapper.toPage(pageable);
        }
        
        // Cache miss - fetch from database
        log.debug("🔍 Cache MISS: Loading messages from database for chat {}", chatId);
        
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        Page<Message> messages = messageRepository.findMessagesByChatId(chatId, pageable);

        log.debug("DEBUG: Found {} messages, total elements: {}",
                messages.getContent().size(), messages.getTotalElements());

        Page<MessageDTO> result = messages.map(message -> convertToMessageDTO(message, userId));
        
        // Cache the result using wrapper
        cacheMessages(chatId, userId, pageable, result);
        
        log.debug("✅ Retrieved {} messages for chat {} and cached", result.getContent().size(), chatId);
        
        return result;
    }
    
    /**
     * Get cached messages using cache wrapper to avoid Page serialization issues
     */
    @Cacheable(value = "chat-messages", key = "#chatId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize + ':user:' + #userId", 
               unless = "#result == null")
    public PageCacheWrapper<MessageDTO> getCachedMessages(Long chatId, Long userId, Pageable pageable) {
        // This method will only be called if cache is empty - return null to indicate cache miss
        return null;
    }
    
    /**
     * Cache messages using wrapper to avoid Page serialization issues
     */
    @CachePut(value = "chat-messages", key = "#chatId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize + ':user:' + #userId")
    public PageCacheWrapper<MessageDTO> cacheMessages(Long chatId, Long userId, Pageable pageable, Page<MessageDTO> page) {
        return PageCacheWrapper.of(page);
    }

    /**
     * Get message by ID
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "message-details", key = "#messageId + ':user:' + #userId", unless = "#result == null")
    public MessageDTO getMessageById(Long messageId, Long userId) {
        log.debug("📩 Fetching message {} by user {}", messageId, userId);
        
        DatabaseContextHolder.setUserContext(userId.toString());
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Verify user is participant of the chat
        if (!chatRepository.isUserParticipantOfChat(message.getChat().getId(), userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        MessageDTO result = convertToMessageDTO(message, userId);
        log.debug("✅ Retrieved message {}", messageId);
        
        return result;
    }

    /**
     * Edit a message
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "chat-messages", key = "#result.chat.id + ':*'", allEntries = false),
        @CacheEvict(value = "message-details", key = "#messageId + ':*'", allEntries = false)
    })
    public MessageDTO editMessage(Long messageId, String newContent, Long userId) {
        log.debug("✏️ Editing message {} by user {}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only sender can edit their message
        if (!message.getSender().getId().equals(userId)) {
            throw new RuntimeException("You can only edit your own messages");
        }

        // Can't edit deleted messages
        if (message.getIsDeleted()) {
            throw new RuntimeException("Cannot edit deleted message");
        }

        // Update content
        message.setContent(newContent);
        message.setIsEdited(true);

        Message updatedMessage = messageRepository.save(message);
        MessageDTO messageDTO = convertToMessageDTO(updatedMessage, userId);

        // Broadcast update
        webSocketService.broadcastMessageUpdate(messageDTO);

        log.debug("✅ Message {} edited successfully", messageId);
        return messageDTO;
    }

    /**
     * Delete a message (soft delete)
     */
    @WriteDB(type = WriteDB.OperationType.DELETE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "chat-messages", key = "#result.chat.id + ':*'", allEntries = false),
        @CacheEvict(value = "message-details", key = "#messageId + ':*'", allEntries = false),
        @CacheEvict(value = "message-counts", key = "#result.chat.id")
    })
    public Message deleteMessage(Long messageId, Long userId) {
        log.debug("🗑️ Deleting message {} by user {}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only sender can delete their message, or admins can delete any message
        boolean isSender = message.getSender().getId().equals(userId);
        boolean isAdmin = participantRepository.isUserAdminOrOwner(message.getChat().getId(), userId);

        if (!isSender && !isAdmin) {
            throw new RuntimeException("You don't have permission to delete this message");
        }

        message.softDelete();
        Message deletedMessage = messageRepository.save(message);

        // Broadcast deletion
        webSocketService.broadcastMessageDelete(messageId, message.getChat().getId());

        log.debug("✅ Message {} deleted successfully", messageId);
        return deletedMessage; // Return for cache eviction
    }

    /**
     * Pin/Unpin a message (user-specific)
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    public MessageDTO togglePinMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only allow pinning if user is a participant
        if (!chatRepository.isUserParticipantOfChat(message.getChat().getId(), userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        PinnedMessage existingPin = pinnedMessageRepository.findByUserIdAndMessageId(userId, messageId);
        boolean isNowPinned;
        if (existingPin != null) {
            // Unpin
            pinnedMessageRepository.deleteByUserIdAndMessageId(userId, messageId);
            isNowPinned = false;
        } else {
            // Pin
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            PinnedMessage pin = PinnedMessage.builder()
                    .user(user)
                    .message(message)
                    .build();
            pinnedMessageRepository.save(pin);
            isNowPinned = true;
        }

        MessageDTO messageDTO = convertToMessageDTO(message, userId);
    messageDTO.setIsPinned(isNowPinned); // Optionally set a field in DTO if needed

        // Broadcast pin status change (user-specific, so may want to notify only this user)
        webSocketService.broadcastMessageUpdate(messageDTO);

        return messageDTO;
    }

    /**
     * Add emoji reaction to message
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    public MessageDTO addReaction(Long messageId, ReactionRequestDTO request, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(message.getChat().getId(), userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if reaction already exists
        Optional<MessageReaction> existingReaction = reactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, userId, request.getEmoji());

        if (existingReaction.isPresent()) {
            // Remove existing reaction (toggle behavior)
            reactionRepository.delete(existingReaction.get());
        } else {
            // Add new reaction
            MessageReaction reaction = new MessageReaction(message, user, request.getEmoji());
            reactionRepository.save(reaction);
        }

        MessageDTO messageDTO = convertToMessageDTO(message, userId);

        // Broadcast reaction change
        webSocketService.broadcastReactionUpdate(messageDTO);

        return messageDTO;
    }

    /**
     * Mark message as read with deadlock retry mechanism
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markAsRead(Long messageId, Long userId) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                markAsReadWithTransaction(messageId, userId);
                return; // Success, exit retry loop
            } catch (Exception e) {
                retryCount++;
                
                // Check if it's a deadlock or lock timeout
                if (isDeadlockException(e) && retryCount < maxRetries) {
                    log.warn("Deadlock detected on markAsRead attempt {} for message {} user {}, retrying...", 
                            retryCount, messageId, userId);
                    
                    // Exponential backoff with jitter
                    try {
                        long delay = (long) (Math.pow(2, retryCount) * 100 + Math.random() * 100);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during deadlock retry", ie);
                    }
                } else {
                    // Re-throw if not a deadlock or max retries reached
                    throw new RuntimeException("Failed to mark message as read after " + maxRetries + " attempts", e);
                }
            }
        }
    }

    /**
     * Internal method for marking message as read with proper transaction boundaries
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    private void markAsReadWithTransaction(Long messageId, Long userId) {
        // Process in specific order to avoid deadlocks: message -> read_status -> participant
        
        // 1. First, get the message (shared lock)
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // 2. Update read status with proper locking
        markAsReadForUserWithLocking(messageId, userId);

        // 3. Update participant's last seen (separate from read status to reduce lock time)
        updateParticipantLastSeen(message.getChat().getId(), userId, messageId);

        // 4. Broadcast update after successful transaction
        webSocketService.broadcastReadStatusUpdate(message.getChat().getId(), messageId, userId);
    }

    /**
     * Check if exception is related to deadlock
     */
    private boolean isDeadlockException(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        
        return message.contains("Deadlock found") || 
               message.contains("Lock wait timeout") ||
               message.contains("deadlock") ||
               e.getCause() != null && isDeadlockException((Exception) e.getCause());
    }

    /**
     * Mark all messages in chat as read with bulk update optimization
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markAllAsRead(Long chatId, Long userId) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                markAllAsReadWithTransaction(chatId, userId);
                return; // Success, exit retry loop
            } catch (Exception e) {
                retryCount++;
                
                if (isDeadlockException(e) && retryCount < maxRetries) {
                    log.warn("Deadlock detected on markAllAsRead attempt {} for chat {} user {}, retrying...", 
                            retryCount, chatId, userId);
                    
                    try {
                        long delay = (long) (Math.pow(2, retryCount) * 150 + Math.random() * 100);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during deadlock retry", ie);
                    }
                } else {
                    throw new RuntimeException("Failed to mark all messages as read after " + maxRetries + " attempts", e);
                }
            }
        }
    }

    /**
     * Internal method for bulk marking messages as read
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    private void markAllAsReadWithTransaction(Long chatId, Long userId) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        // Process messages in smaller batches to reduce lock time
        List<Message> messages = messageRepository.findMessagesByChatIdOrderByCreatedAt(chatId);
        List<Message> unreadMessages = messages.stream()
                .filter(msg -> !msg.getSender().getId().equals(userId))
                .collect(Collectors.toList());

        // Process in batches of 20 to reduce lock contention
        int batchSize = 20;
        for (int i = 0; i < unreadMessages.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, unreadMessages.size());
            List<Message> batch = unreadMessages.subList(i, endIndex);
            
            processBatchReadStatus(batch, userId);
        }

        // Update participant's last seen separately
        updateParticipantLastSeenForChat(chatId, userId);

        // Broadcast read status update for the entire chat
        webSocketService.broadcastChatReadUpdate(chatId, userId);
    }

    /**
     * Process a batch of messages for read status
     */
    private void processBatchReadStatus(List<Message> messages, Long userId) {
        for (Message message : messages) {
            try {
                markAsReadForUserWithLocking(message.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to mark message {} as read for user {}: {}", 
                        message.getId(), userId, e.getMessage());
                // Continue with other messages in batch instead of failing entire operation
            }
        }
    }

    /**
     * Update participant's last seen for the entire chat
     */
    private void updateParticipantLastSeenForChat(Long chatId, Long userId) {
        ChatParticipant participant = participantRepository
                .findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.updateLastSeen();
        participantRepository.save(participant);
    }

    /**
     * Search messages in a chat
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<MessageDTO> searchMessages(Long chatId, String searchTerm, Long userId, Pageable pageable) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        Page<Message> messages = messageRepository.searchMessagesInChat(chatId, searchTerm, pageable);
        return messages.map(message -> convertToMessageDTO(message, userId));
    }

    /**
     * Get pinned messages in a chat (user-specific)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public List<MessageDTO> getPinnedMessages(Long chatId, Long userId) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        List<PinnedMessage> pins = pinnedMessageRepository.findByUserIdAndMessage_Chat_Id(userId, chatId);
        return pins.stream()
                .map(pin -> convertToMessageDTO(pin.getMessage(), userId))
                .collect(Collectors.toList());
    }

    /**
     * Get media messages in a chat
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<MessageDTO> getMediaMessages(Long chatId, Long userId, Pageable pageable) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        Page<Message> mediaMessages = messageRepository.findMediaMessagesInChat(chatId, pageable);
        return mediaMessages.map(message -> convertToMessageDTO(message, userId));
    }

    // Helper methods
    private void createReadStatusForAllParticipants(Message message) {
        List<ChatParticipant> participants = participantRepository
                .findByChatIdAndActive(message.getChat().getId());

        for (ChatParticipant participant : participants) {
            if (!participant.getUser().getId().equals(message.getSender().getId())) {
                MessageReadStatus readStatus = new MessageReadStatus(message, participant.getUser());
                readStatus.markAsDelivered(); // Assume immediate delivery
                readStatusRepository.save(readStatus);
            }
        }
    }

    /**
     * Mark message as read with proper locking to prevent deadlocks
     */
    private void markAsReadForUserWithLocking(Long messageId, Long userId) {
        try {
            // Use UPSERT approach to minimize lock time and avoid race conditions
            Optional<MessageReadStatus> readStatus = readStatusRepository
                    .findByMessageIdAndUserId(messageId, userId);

            if (readStatus.isPresent()) {
                MessageReadStatus status = readStatus.get();
                if (status.getReadAt() == null) { // Only update if not already read
                    status.markAsRead();
                    readStatusRepository.save(status);
                }
            } else {
                // Handle race condition where multiple threads try to create the same record
                try {
                    Message message = messageRepository.findById(messageId)
                            .orElseThrow(() -> new RuntimeException("Message not found"));
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));

                    MessageReadStatus newReadStatus = new MessageReadStatus(message, user);
                    newReadStatus.markAsRead();
                    readStatusRepository.save(newReadStatus);
                } catch (Exception e) {
                    // Handle unique constraint violation - another thread created the record
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                        // Retry finding the record that was just created
                        Optional<MessageReadStatus> retryStatus = readStatusRepository
                                .findByMessageIdAndUserId(messageId, userId);
                        if (retryStatus.isPresent() && retryStatus.get().getReadAt() == null) {
                            retryStatus.get().markAsRead();
                            readStatusRepository.save(retryStatus.get());
                        }
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error marking message {} as read for user {}: {}", messageId, userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Update participant's last seen status separately to reduce lock contention
     */
    private void updateParticipantLastSeen(Long chatId, Long userId, Long messageId) {
        ChatParticipant participant = participantRepository
                .findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.markAsRead(messageId);
        participantRepository.save(participant);
    }

    /**
     * Legacy method for backward compatibility
     */
    private void markAsReadForUser(Long messageId, Long userId) {
        markAsReadForUserWithLocking(messageId, userId);
    }

    private MessageDTO convertToMessageDTO(Message message, Long currentUserId) {
        // Use the constructor from MessageDTO since it handles conversion
        MessageDTO dto = new MessageDTO(message);

        // Set additional fields that might not be handled by constructor
        if (message.getSender() != null) {
            UserDTO senderDTO = new UserDTO();
            senderDTO.setId(message.getSender().getId());
            senderDTO.setUsername(message.getSender().getUsername());
            senderDTO.setEmail(message.getSender().getEmail());
            senderDTO.setProfileImageUrl(message.getSender().getProfileImageUrl());
            senderDTO.setAvatar(message.getSender().getAvatar());
            dto.setSender(senderDTO);
        }

        // Set reply message if exists
        if (message.getReplyToId() != null) {
            Optional<Message> replyMessage = messageRepository.findById(message.getReplyToId());
            if (replyMessage.isPresent()) {
                dto.setReplyToMessage(new MessageDTO(replyMessage.get()));
            }
        }

        // Set read status for current user
        Optional<MessageReadStatus> readStatus = readStatusRepository
                .findByMessageIdAndUserId(message.getId(), currentUserId);
        dto.setIsRead(readStatus.isPresent() && readStatus.get().getReadAt() != null);
        dto.setIsDelivered(readStatus.isPresent() && readStatus.get().getDeliveredAt() != null);

        // Count how many users have read this message
        Long readCount = readStatusRepository.countReadByMessageId(message.getId());
        dto.setReadByCount(readCount != null ? readCount.intValue() : 0);

        return dto;
    }
}
