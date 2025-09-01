package com.example.DPMHC_backend.service;
import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

    /**
     * Send a new message
     */
    @Transactional
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Page<MessageDTO> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        // ADD THIS DEBUG LOGGING:
        log.info("DEBUG: Querying messages for chatId: {}, pageable: {}", chatId, pageable);

        Page<Message> messages = messageRepository.findMessagesByChatId(chatId, pageable);

        log.info("DEBUG: Found {} messages, total elements: {}",
                messages.getContent().size(), messages.getTotalElements());

        messages.getContent().forEach(msg ->
                log.info("DEBUG: Message - ID: {}, Type: {}, MediaUrl: {}, CreatedAt: {}",
                        msg.getId(), msg.getMessageType(), msg.getMediaUrl(), msg.getCreatedAt())
        );

        return messages.map(message -> convertToMessageDTO(message, userId));
    }

    /**
     * Get message by ID
     */
    @Transactional(readOnly = true)
    public MessageDTO getMessageById(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Verify user is participant of the chat
        if (!chatRepository.isUserParticipantOfChat(message.getChat().getId(), userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        return convertToMessageDTO(message, userId);
    }

    /**
     * Edit a message
     */
    public MessageDTO editMessage(Long messageId, String newContent, Long userId) {
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

        return messageDTO;
    }

    /**
     * Delete a message (soft delete)
     */
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only sender can delete their message, or admins can delete any message
        boolean isSender = message.getSender().getId().equals(userId);
        boolean isAdmin = participantRepository.isUserAdminOrOwner(message.getChat().getId(), userId);

        if (!isSender && !isAdmin) {
            throw new RuntimeException("You don't have permission to delete this message");
        }

        message.softDelete();
        messageRepository.save(message);

        // Broadcast deletion
        webSocketService.broadcastMessageDelete(messageId, message.getChat().getId());
    }

    /**
     * Pin/Unpin a message (user-specific)
     */
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
     * Mark message as read
     */
    public void markAsRead(Long messageId, Long userId) {
        markAsReadForUser(messageId, userId);

        // Update participant's last seen
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        ChatParticipant participant = participantRepository
                .findByChatIdAndUserId(message.getChat().getId(), userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.markAsRead(messageId);
        participantRepository.save(participant);

        // Broadcast read status update
        webSocketService.broadcastReadStatusUpdate(message.getChat().getId(), messageId, userId);
    }

    /**
     * Mark all messages in chat as read
     */
    public void markAllAsRead(Long chatId, Long userId) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        // Get all unread messages
        List<Message> messages = messageRepository.findMessagesByChatIdOrderByCreatedAt(chatId);

        for (Message message : messages) {
            if (!message.getSender().getId().equals(userId)) { // Don't mark own messages
                markAsReadForUser(message.getId(), userId);
            }
        }

        // Update participant's last seen to now
        ChatParticipant participant = participantRepository
                .findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.updateLastSeen();
        participantRepository.save(participant);

        // Broadcast read status update for the entire chat
        webSocketService.broadcastChatReadUpdate(chatId, userId);
    }

    /**
     * Search messages in a chat
     */
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

    private void markAsReadForUser(Long messageId, Long userId) {
        Optional<MessageReadStatus> readStatus = readStatusRepository
                .findByMessageIdAndUserId(messageId, userId);

        if (readStatus.isPresent()) {
            readStatus.get().markAsRead();
            readStatusRepository.save(readStatus.get());
        } else {
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            MessageReadStatus newReadStatus = new MessageReadStatus(message, user);
            newReadStatus.markAsRead();
            readStatusRepository.save(newReadStatus);
        }
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
