package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final com.example.DPMHC_backend.service.WebSocketService webSocketService;

    /**
     * Create a new chat
     */
    @WriteDB(type = WriteDB.OperationType.CREATE)
    public ChatDTO createChat(ChatCreateRequestDTO request, Long creatorId) {
        log.info("Creating new chat for user: {}", creatorId);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        // For private chats, check if chat already exists
        if (request.getChatType() == Chat.ChatType.PRIVATE && request.getParticipantIds().size() == 1) {
            Long otherUserId = request.getParticipantIds().get(0);
            Optional<Chat> existingChat = chatRepository.findPrivateChatBetweenUsers(creatorId, otherUserId);
            if (existingChat.isPresent()) {
                return convertToChatDTO(existingChat.get(), creatorId);
            }
        }

        // Create new chat
        Chat chat = new Chat();
        chat.setChatName(request.getChatName());
        chat.setChatType(request.getChatType());
        chat.setChatImageUrl(request.getChatImageUrl());
        chat.setDescription(request.getDescription());
        chat.setCreatedBy(creatorId);

        // Set default name for private chats
        if (request.getChatType() == Chat.ChatType.PRIVATE && request.getChatName() == null) {
            if (request.getParticipantIds().size() == 1) {
                User otherUser = userRepository.findById(request.getParticipantIds().get(0))
                        .orElseThrow(() -> new RuntimeException("Participant not found"));
                chat.setChatName(otherUser.getUsername());
            }
        }

        Chat savedChat = chatRepository.save(chat);

        // Add creator as owner
        addParticipantToChat(savedChat, creator, ChatParticipant.ParticipantRole.OWNER);

        // Add other participants
        for (Long participantId : request.getParticipantIds()) {
            if (!participantId.equals(creatorId)) {
                User participant = userRepository.findById(participantId)
                        .orElseThrow(() -> new RuntimeException("Participant not found: " + participantId));
                addParticipantToChat(savedChat, participant, ChatParticipant.ParticipantRole.MEMBER);
            }
        }

        ChatDTO chatDTO = convertToChatDTO(savedChat, creatorId);

        // Notify participants via WebSocket
        webSocketService.notifyNewChat(chatDTO);

        log.info("Created chat with ID: {}", savedChat.getId());
        return chatDTO;
    }

    /**
     * Get user's chats with pagination
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<ChatDTO> getUserChats(Long userId, Pageable pageable) {
        Page<Chat> chats = chatRepository.findChatsByUserId(userId, pageable);
        return chats.map(chat -> convertToChatDTO(chat, userId));
    }

    /**
     * Get user's chats as list
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public List<ChatDTO> getUserChatsList(Long userId) {
        List<Chat> chats = chatRepository.findChatsByUserId(userId);
        return chats.stream()
                .map(chat -> convertToChatDTO(chat, userId))
                .collect(Collectors.toList());
    }

    /**
     * Get specific chat by ID
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public ChatDTO getChatById(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        return convertToChatDTO(chat, userId);
    }

    /**
     * Update chat settings
     */
    public ChatDTO updateChat(Long chatId, ChatUpdateRequestDTO request, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Check if user is admin or owner
        if (!participantRepository.isUserAdminOrOwner(chatId, userId)) {
            throw new RuntimeException("Only admins can update chat settings");
        }

        if (request.getChatName() != null) {
            chat.setChatName(request.getChatName());
        }
        if (request.getChatImageUrl() != null) {
            chat.setChatImageUrl(request.getChatImageUrl());
        }
        if (request.getDescription() != null) {
            chat.setDescription(request.getDescription());
        }

        Chat updatedChat = chatRepository.save(chat);
        ChatDTO chatDTO = convertToChatDTO(updatedChat, userId);

        // Notify participants
        webSocketService.notifyChatUpdated(chatDTO);

        return chatDTO;
    }

    /**
     * Add participants to chat
     */
    public ChatDTO addParticipants(Long chatId, AddParticipantsRequestDTO request, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Check if user is admin or owner (for group chats)
        if (chat.getChatType() == Chat.ChatType.GROUP &&
                !participantRepository.isUserAdminOrOwner(chatId, userId)) {
            throw new RuntimeException("Only admins can add participants to group chats");
        }

        for (Long participantId : request.getUserIds()) {
            // Check if user is already a participant
            Optional<ChatParticipant> existingParticipant =
                    participantRepository.findByChatIdAndUserId(chatId, participantId);

            if (existingParticipant.isEmpty()) {
                User participant = userRepository.findById(participantId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + participantId));
                addParticipantToChat(chat, participant, ChatParticipant.ParticipantRole.MEMBER);
            }
        }

        ChatDTO chatDTO = convertToChatDTO(chat, userId);

        // Notify all participants
        webSocketService.notifyChatUpdated(chatDTO);

        return chatDTO;
    }

    /**
     * Remove participant from chat
     */
    public void removeParticipant(Long chatId, Long participantId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Check permissions
        boolean isAdmin = participantRepository.isUserAdminOrOwner(chatId, userId);
        boolean isSelfLeaving = participantId.equals(userId);

        if (!isAdmin && !isSelfLeaving) {
            throw new RuntimeException("You don't have permission to remove this participant");
        }

        ChatParticipant participant = participantRepository.findByChatIdAndUserId(chatId, participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.leaveChat();
        participantRepository.save(participant);

        // Notify participants
        webSocketService.notifyParticipantLeft(chatId, participantId);
    }

    /**
     * Delete entire chat
     */
    public void deleteChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Only owner can delete the entire chat
        ChatParticipant userParticipant = participantRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("You are not a participant of this chat"));

        if (userParticipant.getRole() != ChatParticipant.ParticipantRole.OWNER) {
            throw new RuntimeException("Only the chat owner can delete the chat");
        }

        chat.setIsActive(false);
        chatRepository.save(chat);

        // Notify all participants
        webSocketService.notifyChatDeleted(chatId);
    }

    /**
     * Search chats by name
     */
    @Transactional(readOnly = true)
    public List<ChatDTO> searchChats(Long userId, String searchTerm) {
        List<Chat> chats = chatRepository.searchChatsByName(userId, searchTerm);
        return chats.stream()
                .map(chat -> convertToChatDTO(chat, userId))
                .collect(Collectors.toList());
    }

    /**
     * Get chat participants
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantDTO> getChatParticipants(Long chatId, Long userId) {
        // Verify user is participant
        if (!chatRepository.isUserParticipantOfChat(chatId, userId)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        List<ChatParticipant> participants = participantRepository.findByChatIdAndActive(chatId);
        return participants.stream()
                .map(this::convertToParticipantDTO)
                .collect(Collectors.toList());
    }

    // Helper methods
    private void addParticipantToChat(Chat chat, User user, ChatParticipant.ParticipantRole role) {
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        participant.setUser(user);
        participant.setRole(role);
        participantRepository.save(participant);
    }

    private ChatDTO convertToChatDTO(Chat chat, Long currentUserId) {
        ChatDTO dto = ChatDTO.fromEntity(chat);

        // Get participants
        List<ChatParticipant> participants = participantRepository.findByChatIdAndActive(chat.getId());
        dto.setParticipants(participants.stream()
                .map(this::convertToParticipantDTO)
                .collect(Collectors.toList()));

        // Get last message
        Optional<Message> lastMessage = messageRepository.findLatestMessageInChat(chat.getId());
        if (lastMessage.isPresent()) {
            dto.setLastMessage(MessageDTO.fromEntity(lastMessage.get()));
        }

        // Get unread count for current user
        Long unreadCount = messageRepository.countUnreadMessagesForUserInChat(chat.getId(), currentUserId);
        dto.setUnreadCount(unreadCount.intValue());

        // Set user-specific flags
        Optional<ChatParticipant> currentUserParticipant =
                participantRepository.findByChatIdAndUserId(chat.getId(), currentUserId);
        if (currentUserParticipant.isPresent()) {
            ChatParticipant participant = currentUserParticipant.get();
            dto.setIsMuted(participant.getIsMuted());
            dto.setIsAdmin(participant.getIsAdmin());
            dto.setIsOwner(participant.isOwner());
        }

        return dto;
    }

    private ChatParticipantDTO convertToParticipantDTO(ChatParticipant participant) {
        ChatParticipantDTO dto = new ChatParticipantDTO();
        dto.setId(participant.getId());
        dto.setUser(new UserDTO(participant.getUser()));
        dto.setRole(participant.getRole().toString());
        dto.setIsMuted(participant.getIsMuted());
        dto.setIsAdmin(participant.getIsAdmin());
        dto.setJoinedAt(participant.getJoinedAt().toString());
        dto.setLastSeenAt(participant.getLastSeenAt() != null ? participant.getLastSeenAt().toString() : null);
        dto.setIsActive(participant.getIsActive());
        dto.setIsOnline(false); // Implement real-time online status
        return dto;
    }
}
