package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.ChatDTO;
import com.example.DPMHC_backend.dto.MessageDTO;
import com.example.DPMHC_backend.model.ChatParticipant;
import com.example.DPMHC_backend.repository.ChatParticipantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatParticipantRepository participantRepository;
    private final ObjectMapper objectMapper;

    // Store active user sessions
    private final Map<Long, String> activeUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    public WebSocketService(@Lazy SimpMessagingTemplate messagingTemplate,
                            ChatParticipantRepository participantRepository,
                            ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.participantRepository = participantRepository;
        this.objectMapper = objectMapper;
    }
    /**
     * Register user session
     */
    public void registerUserSession(Long userId, String sessionId) {
        activeUsers.put(userId, sessionId);
        sessionToUser.put(sessionId, userId);
        log.info("User {} connected with session {}", userId, sessionId);

        // Notify user's contacts about online status
        notifyUserOnlineStatus(userId, true);
    }

    /**
     * Unregister user session
     */
    public void unregisterUserSession(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            activeUsers.remove(userId);
            log.info("User {} disconnected with session {}", userId, sessionId);

            // Notify user's contacts about offline status
            notifyUserOnlineStatus(userId, false);
        }
    }

    /**
     * Check if user is online
     */
    public boolean isUserOnline(Long userId) {
        return activeUsers.containsKey(userId);
    }

    /**
     * Broadcast new message to all chat participants
     */
    public void broadcastNewMessage(MessageDTO message) {
        try {
            // Get all participants of the chat
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(message.getChatId());

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "NEW_MESSAGE",
                    "data", message,
                    "timestamp", LocalDateTime.now()
            ));

            // Send to each participant
            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/messages",
                            messageJson
                    );
                }
            }

            log.debug("Broadcasted message {} to {} participants",
                    message.getId(), participants.size());

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting message", e);
        }
    }

    /**
     * Broadcast message update (edit/pin)
     */
    public void broadcastMessageUpdate(MessageDTO message) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(message.getChatId());

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "MESSAGE_UPDATED",
                    "data", message,
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/messages",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting message update", e);
        }
    }

    /**
     * Broadcast message deletion
     */
    public void broadcastMessageDelete(Long messageId, Long chatId) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "MESSAGE_DELETED",
                    "data", Map.of(
                            "messageId", messageId,
                            "chatId", chatId
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/messages",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting message deletion", e);
        }
    }

    /**
     * Broadcast reaction update
     */
    public void broadcastReactionUpdate(MessageDTO message) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(message.getChatId());

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "REACTION_UPDATED",
                    "data", message,
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/reactions",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting reaction update", e);
        }
    }

    /**
     * Broadcast typing indicator
     */
    public void broadcastTypingIndicator(Long chatId, Long userId, boolean isTyping) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "TYPING_INDICATOR",
                    "data", Map.of(
                            "chatId", chatId,
                            "userId", userId,
                            "isTyping", isTyping
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            // Send to all participants except the typing user
            for (ChatParticipant participant : participants) {
                Long participantId = participant.getUser().getId();
                if (!participantId.equals(userId) && isUserOnline(participantId)) {
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/typing",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting typing indicator", e);
        }
    }

    /**
     * Broadcast read status update
     */
    public void broadcastReadStatusUpdate(Long chatId, Long messageId, Long userId) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "READ_STATUS_UPDATED",
                    "data", Map.of(
                            "chatId", chatId,
                            "messageId", messageId,
                            "userId", userId
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long participantId = participant.getUser().getId();
                if (isUserOnline(participantId)) {
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/read-status",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting read status update", e);
        }
    }

    /**
     * Broadcast chat read update (all messages marked as read)
     */
    public void broadcastChatReadUpdate(Long chatId, Long userId) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "CHAT_READ_UPDATED",
                    "data", Map.of(
                            "chatId", chatId,
                            "userId", userId
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long participantId = participant.getUser().getId();
                if (isUserOnline(participantId)) {
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/read-status",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error broadcasting chat read update", e);
        }
    }

    /**
     * Notify about new chat creation
     */
    public void notifyNewChat(ChatDTO chat) {
        try {
            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "NEW_CHAT",
                    "data", chat,
                    "timestamp", LocalDateTime.now()
            ));

            // Send to all participants
            if (chat.getParticipants() != null) {
                for (var participant : chat.getParticipants()) {
                    Long userId = participant.getUser().getId();
                    if (isUserOnline(userId)) {
                        messagingTemplate.convertAndSendToUser(
                                userId.toString(),
                                "/queue/chats",
                                messageJson
                        );
                    }
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error notifying new chat", e);
        }
    }

    /**
     * Notify about chat updates
     */
    public void notifyChatUpdated(ChatDTO chat) {
        try {
            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "CHAT_UPDATED",
                    "data", chat,
                    "timestamp", LocalDateTime.now()
            ));

            if (chat.getParticipants() != null) {
                for (var participant : chat.getParticipants()) {
                    Long userId = participant.getUser().getId();
                    if (isUserOnline(userId)) {
                        messagingTemplate.convertAndSendToUser(
                                userId.toString(),
                                "/queue/chats",
                                messageJson
                        );
                    }
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error notifying chat update", e);
        }
    }

    /**
     * Notify about chat deletion
     */
    public void notifyChatDeleted(Long chatId) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "CHAT_DELETED",
                    "data", Map.of("chatId", chatId),
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/chats",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error notifying chat deletion", e);
        }
    }

    /**
     * Notify when participant leaves chat
     */
    public void notifyParticipantLeft(Long chatId, Long participantId) {
        try {
            List<ChatParticipant> participants = participantRepository
                    .findByChatIdAndActive(chatId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "PARTICIPANT_LEFT",
                    "data", Map.of(
                            "chatId", chatId,
                            "participantId", participantId
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            for (ChatParticipant participant : participants) {
                Long userId = participant.getUser().getId();
                if (isUserOnline(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/chats",
                            messageJson
                    );
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error notifying participant left", e);
        }
    }

    /**
     * Notify user's contacts about online status change
     */
    private void notifyUserOnlineStatus(Long userId, boolean isOnline) {
        try {
            // Get all chats where user is participant
            List<ChatParticipant> userChats = participantRepository.findByUserIdAndActive(userId);

            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "type", "USER_STATUS_CHANGED",
                    "data", Map.of(
                            "userId", userId,
                            "isOnline", isOnline
                    ),
                    "timestamp", LocalDateTime.now()
            ));

            // Notify all users who share chats with this user
            for (ChatParticipant userChat : userChats) {
                List<ChatParticipant> chatParticipants = participantRepository
                        .findByChatIdAndActive(userChat.getChat().getId());

                for (ChatParticipant participant : chatParticipants) {
                    Long participantId = participant.getUser().getId();
                    if (!participantId.equals(userId) && isUserOnline(participantId)) {
                        messagingTemplate.convertAndSendToUser(
                                participantId.toString(),
                                "/queue/user-status",
                                messageJson
                        );
                    }
                }
            }

        } catch (JsonProcessingException e) {
            log.error("Error notifying user status change", e);
        }
    }

    /**
     * Send private notification to specific user
     */
    public void sendPrivateNotification(Long userId, String type, Object data) {
        try {
            if (isUserOnline(userId)) {
                String messageJson = objectMapper.writeValueAsString(Map.of(
                        "type", type,
                        "data", data,
                        "timestamp", LocalDateTime.now()
                ));

                messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        "/queue/notifications",
                        messageJson
                );
            }
        } catch (JsonProcessingException e) {
            log.error("Error sending private notification", e);
        }
    }
}