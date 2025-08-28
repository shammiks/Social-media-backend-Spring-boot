package com.example.DPMHC_backend.service;
import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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
    private final com.example.DPMHC_backend.service.WebSocketService webSocketService;

    /**
     * Send a new message
     */
    public MessageDTO sendMessage(MessageSendRequestDTO request, Long senderId) {
        log.info("Sending message to chat: {} by user: {}", request.getChatId(), senderId);

        // Validate request
        if (!request.isValid()) {
            throw new RuntimeException("Invalid message content");
        }

        // Verify chat exists and user is participant
        Chat chat = chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        if (!chatRepository.isUserParticipantOfChat(request.getChatId(), senderId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        // Create message
        Message message = new Message();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setMessageType(request.getMessageType());
        message.setMediaUrl(request.getMediaUrl());
        message.setMediaType(request.getMediaType());
        message.setMediaSize(request.getMediaSize());
        message.setThumbnailUrl(request.getThumbnailUrl());
        message.setReplyToId(request.getReplyToId());

        Message savedMessage = messageRepository.save(message);

        // Update chat's last message time
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        // Create read status for all participants
        createReadStatusForAllParticipants(savedMessage);

        // Mark as read for sender
        markAsReadForUser(savedMessage.getId(), senderId);

        MessageDTO messageDTO = convertToMessageDTO(savedMessage, senderId);

        // Send real-time notification
        webSocketService.broadcastMessage(messageDTO);

        log.info("Message sent with ID: {}", savedMessage.getId());
        return messageDTO;
    }

    /**
     * Get messages in a chat with pagination
     */
    @Transactional(readOnly = true)
    public Page<MessageDTO> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        Page<Message> messages = messageRepository.findMessagesByChatId(chatId, pageable);
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
     * Pin/Unpin a message
     */
    public MessageDTO togglePinMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only admins can pin messages
        if (!participantRepository.isUserAdminOrOwner(message.getChat().getId(), userId)) {
            throw new RuntimeException("Only admins can pin messages");
        }

        message.setIsPinned(!message.getIsPinned());
        Message updatedMessage = messageRepository.save(message);

        MessageDTO messageDTO = convertToMessageDTO(updatedMessage, userId);

        // Broadcast pin status change
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
     * Get pinned messages in a chat
     */
    @Transactional(readOnly = true)
    public List<MessageDTO> getPinnedMessages(Long chatId, Long userId) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        List<Message> pinnedMessages = messageRepository.findPinnedMessagesInChat(chatId);
        return pinnedMessages.stream()
                .map(message -> convertToMessageDTO(message, userId))
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
        MessageDTO dto = MessageDTO.fromEntity(message);

        // Set sender info
        if (message.getSender() != null) {
            dto.setSender(new UserDTO(message.getSender()));
        }

        // Set reply-to message if exists
        if (message.getReplyToId() != null) {
            Optional<Message> replyToMessage = messageRepository.findById(message.getReplyToId());
            if (replyToMessage.isPresent() && !replyToMessage.get().getIsDeleted()) {
                dto.setReplyToMessage(MessageDTO.fromEntity(replyToMessage.get()));
            }
        }

        // Set read status for current user
        Optional<MessageReadStatus> readStatus = readStatusRepository
                .findByMessageIdAndUserId(message.getId(), currentUserId);
        if (readStatus.isPresent()) {
            dto.setIsRead(readStatus.get().getReadAt() != null);
            dto.setIsDelivered(readStatus.get().getIsDelivered());
        }

        // Count how many users have read this message
        Long readCount = readStatusRepository.countReadByMessageId(message.getId());
        dto.setReadByCount(readCount.intValue());

        return dto;
    }
}
