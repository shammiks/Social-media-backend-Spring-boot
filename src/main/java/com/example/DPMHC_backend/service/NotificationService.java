// Enhanced NotificationService.java - Fixed state persistence issues

package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.NotificationDTO;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.NotificationRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // ======================== ENHANCED NOTIFICATION STATE MANAGEMENT ========================

    @Transactional
    public NotificationDTO markAsReadAndReturn(Long notificationId, String userEmail) {
        log.debug("Marking notification {} as read for user {}", notificationId, userEmail);

        Notification notification = getNotificationById(notificationId);

        // Verify the notification belongs to the user
        if (!notification.getRecipient().getEmail().equals(userEmail)) {
            throw new RuntimeException("Notification does not belong to user: " + userEmail);
        }

        // Mark as read
        notification.markAsRead();
        notification = notificationRepository.save(notification);

        log.info("Successfully marked notification {} as read for user {}", notificationId, userEmail);

        // Send real-time update
        sendNotificationStateUpdate(notification);

        return convertToDTO(notification);
    }

    @Transactional
    public NotificationDTO markAsUnreadAndReturn(Long notificationId, String userEmail) {
        log.debug("Marking notification {} as unread for user {}", notificationId, userEmail);

        Notification notification = getNotificationById(notificationId);

        // Verify the notification belongs to the user
        if (!notification.getRecipient().getEmail().equals(userEmail)) {
            throw new RuntimeException("Notification does not belong to user: " + userEmail);
        }

        // Mark as unread
        notification.markAsUnread();
        notification = notificationRepository.save(notification);

        log.info("Successfully marked notification {} as unread for user {}", notificationId, userEmail);

        // Send real-time update
        sendNotificationStateUpdate(notification);

        return convertToDTO(notification);
    }

    @Transactional
    public NotificationDTO markAsSeenAndReturn(Long notificationId, String userEmail) {
        log.debug("Marking notification {} as seen for user {}", notificationId, userEmail);

        Notification notification = getNotificationById(notificationId);

        // Verify the notification belongs to the user
        if (!notification.getRecipient().getEmail().equals(userEmail)) {
            throw new RuntimeException("Notification does not belong to user: " + userEmail);
        }

        // Mark as seen
        notification.markAsSeen();
        notification = notificationRepository.save(notification);

        log.info("Successfully marked notification {} as seen for user {}", notificationId, userEmail);

        // Send real-time update
        sendNotificationStateUpdate(notification);

        return convertToDTO(notification);
    }

    @Transactional
    public void deleteNotificationForUser(Long notificationId, String userEmail) {
        log.debug("Deleting notification {} for user {}", notificationId, userEmail);

        Notification notification = getNotificationById(notificationId);

        // Verify the notification belongs to the user
        if (!notification.getRecipient().getEmail().equals(userEmail)) {
            throw new RuntimeException("Notification does not belong to user: " + userEmail);
        }

        notificationRepository.delete(notification);

        log.info("Successfully deleted notification {} for user {}", notificationId, userEmail);

        // Send real-time update about deletion
        sendNotificationDeletionUpdate(notification);
    }

    @Transactional
    public int markMultipleAsReadForUser(List<Long> notificationIds, String userEmail) {
        log.debug("Marking {} notifications as read for user {}", notificationIds.size(), userEmail);

        User user = getUserByEmail(userEmail);

        // Get notifications and verify they belong to the user
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);

        // Filter to only include notifications that belong to the user
        List<Long> validIds = notifications.stream()
                .filter(n -> n.getRecipient().equals(user))
                .map(Notification::getId)
                .collect(Collectors.toList());

        if (validIds.isEmpty()) {
            log.warn("No valid notifications found for user {} in provided IDs", userEmail);
            return 0;
        }

        int updatedCount = notificationRepository.markNotificationsAsRead(validIds, new Date());

        log.info("Successfully marked {} notifications as read for user {}", updatedCount, userEmail);

        // Send real-time updates
        sendBulkNotificationUpdate(user);

        return updatedCount;
    }

    // ======================== ORIGINAL METHODS WITH ENHANCEMENTS ========================

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = getNotificationById(notificationId);
        notification.markAsRead();
        notificationRepository.save(notification);

        // Send real-time update
        sendNotificationStateUpdate(notification);
    }

    @Transactional
    public void markAsUnread(Long notificationId) {
        Notification notification = getNotificationById(notificationId);
        notification.markAsUnread();
        notificationRepository.save(notification);

        // Send real-time update
        sendNotificationStateUpdate(notification);
    }

    @Transactional
    public void markAsSeen(Long notificationId) {
        Notification notification = getNotificationById(notificationId);
        notification.markAsSeen();
        notificationRepository.save(notification);

        // Send real-time update
        sendNotificationStateUpdate(notification);
    }

    @Transactional
    public int markAllAsRead(String email) {
        User user = getUserByEmail(email);
        int updatedCount = notificationRepository.markAllAsReadForUser(user, new Date());

        log.info("Marked {} notifications as read for user {}", updatedCount, email);

        // Send real-time update
        sendBulkNotificationUpdate(user);

        return updatedCount;
    }

    @Transactional
    public int markAllAsSeen(String email) {
        User user = getUserByEmail(email);
        int updatedCount = notificationRepository.markAllAsSeenForUser(user);

        log.info("Marked {} notifications as seen for user {}", updatedCount, email);

        // Send real-time update
        sendBulkNotificationUpdate(user);

        return updatedCount;
    }

    // ======================== REAL-TIME NOTIFICATION UPDATES ========================

    private void sendNotificationStateUpdate(Notification notification) {
        try {
            NotificationDTO dto = convertToDTO(notification);
            String destination = "/topic/notifications/" + notification.getRecipient().getId();

            // Send updated notification
            messagingTemplate.convertAndSend(destination + "/update", dto);

            // Send updated counts
            Map<String, Long> counts = getNotificationCountsMap(notification.getRecipient().getEmail());
            messagingTemplate.convertAndSend(destination + "/counts", counts);

            log.debug("Sent notification state update for notification {} to {}",
                    notification.getId(), destination);

        } catch (Exception e) {
            log.error("Error sending notification state update for notification {}: {}",
                    notification.getId(), e.getMessage());
        }
    }

    private void sendNotificationDeletionUpdate(Notification notification) {
        try {
            String destination = "/topic/notifications/" + notification.getRecipient().getId();

            // Send deletion notification
            messagingTemplate.convertAndSend(destination + "/delete", Map.of(
                    "notificationId", notification.getId(),
                    "type", "DELETED"
            ));

            // Send updated counts
            Map<String, Long> counts = getNotificationCountsMap(notification.getRecipient().getEmail());
            messagingTemplate.convertAndSend(destination + "/counts", counts);

            log.debug("Sent notification deletion update for notification {} to {}",
                    notification.getId(), destination);

        } catch (Exception e) {
            log.error("Error sending notification deletion update for notification {}: {}",
                    notification.getId(), e.getMessage());
        }
    }

    private void sendBulkNotificationUpdate(User user) {
        try {
            String destination = "/topic/notifications/" + user.getId();

            // Send updated counts
            Map<String, Long> counts = getNotificationCountsMap(user.getEmail());
            messagingTemplate.convertAndSend(destination + "/counts", counts);

            // Send refresh signal to trigger client-side fetch
            messagingTemplate.convertAndSend(destination + "/refresh", Map.of(
                    "type", "BULK_UPDATE",
                    "timestamp", System.currentTimeMillis()
            ));

            log.debug("Sent bulk notification update to {}", destination);

        } catch (Exception e) {
            log.error("Error sending bulk notification update for user {}: {}",
                    user.getEmail(), e.getMessage());
        }
    }

    // ======================== CORE NOTIFICATION OPERATIONS ========================

    @Transactional
    @Async
    public void createNotification(NotificationBuilder builder) {
        try {
            User recipient = getUserByEmail(builder.getRecipientEmail());

            // Check for duplicate notifications to avoid spam
            if (builder.isCheckDuplicates() && isDuplicateNotification(recipient, builder)) {
                log.debug("Duplicate notification prevented for user {}", recipient.getEmail());
                return;
            }

            Notification notification = buildNotification(recipient, builder);
            notification = notificationRepository.save(notification);

            // Send real-time notification via WebSocket
            sendRealTimeNotification(notification);

            log.info("Notification created: {} for user {}", builder.getType(), recipient.getEmail());
        } catch (Exception e) {
            log.error("Error creating notification", e);
        }
    }

    @Transactional
    @Async
    public void createNotification(Long recipientId, Long actorId, NotificationType type,
                                   Long entityId, String message) {
        try {
            User recipient = userRepository.findById(recipientId)
                    .orElseThrow(() -> new RuntimeException("Recipient not found: " + recipientId));
            User actor = userRepository.findById(actorId)
                    .orElseThrow(() -> new RuntimeException("Actor not found: " + actorId));

            String entityType = determineEntityType(type, entityId);

            NotificationBuilder builder = NotificationBuilder.builder()
                    .recipientEmail(recipient.getEmail())
                    .actor(actor)
                    .type(type)
                    .entityId(entityId)
                    .entityType(entityType)
                    .customMessage(message)
                    .priority(NotificationPriority.NORMAL)
                    .checkDuplicates(true)
                    .generateContent(true)
                    .build();

            createNotification(builder);

            log.info("Notification created: {} from user {} to user {}", type, actorId, recipientId);
        } catch (Exception e) {
            log.error("Error creating notification for recipient {} from actor {}", recipientId, actorId, e);
        }
    }

    @Transactional
    public void deleteNotification(Long id) {
        Notification notification = getNotificationById(id);
        notificationRepository.delete(notification);

        // Send real-time update
        sendNotificationDeletionUpdate(notification);
    }

    @Transactional
    public void createSocialNotification(String recipientEmail, String actorEmail,
                                         NotificationType type, Long entityId, String entityType) {
        User actor = getUserByEmail(actorEmail);

        NotificationBuilder builder = NotificationBuilder.builder()
                .recipientEmail(recipientEmail)
                .actor(actor)
                .type(type)
                .entityId(entityId)
                .entityType(entityType)
                .priority(NotificationPriority.NORMAL)
                .checkDuplicates(true)
                .generateContent(true)
                .build();

        createNotification(builder);
    }

    // ======================== QUERYING AND FILTERING ========================

    public Page<NotificationDTO> getNotifications(String email, Pageable pageable) {
        log.debug("Fetching notifications for user: {}, page: {}, size: {}",
                email, pageable.getPageNumber(), pageable.getPageSize());

        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientOrderByPriorityAndCreatedAt(user, pageable);

        log.debug("Found {} notifications for user: {}", notifications.getContent().size(), email);

        return notifications.map(this::convertToDTO);
    }

    public Page<NotificationDTO> getUnreadNotifications(String email, Pageable pageable) {
        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientAndIsReadOrderByCreatedAtDesc(user, false, pageable);
        return notifications.map(this::convertToDTO);
    }

    public Page<NotificationDTO> getNotificationsByType(String email, NotificationType type, Pageable pageable) {
        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientAndTypeOrderByCreatedAtDesc(user, type, pageable);
        return notifications.map(this::convertToDTO);
    }

    public Page<NotificationDTO> getNotificationsByTypes(String email, List<NotificationType> types, Pageable pageable) {
        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientAndTypeInOrderByCreatedAtDesc(user, types, pageable);
        return notifications.map(this::convertToDTO);
    }

    @Transactional
    public int markMultipleAsRead(List<Long> notificationIds) {
        int updatedCount = notificationRepository.markNotificationsAsRead(notificationIds, new Date());

        // Send real-time updates for affected users
        List<Notification> updatedNotifications = notificationRepository.findAllById(notificationIds);
        Set<User> affectedUsers = updatedNotifications.stream()
                .map(Notification::getRecipient)
                .collect(Collectors.toSet());

        for (User user : affectedUsers) {
            sendBulkNotificationUpdate(user);
        }

        return updatedCount;
    }

    // ======================== STATISTICS AND COUNTS ========================

    public long getUnreadCount(String email) {
        User user = getUserByEmail(email);
        long count = notificationRepository.countByRecipientAndIsRead(user, false);
        log.debug("Unread count for user {}: {}", email, count);
        return count;
    }

    public long getUnseenCount(String email) {
        User user = getUserByEmail(email);
        long count = notificationRepository.countByRecipientAndIsSeenAndIsRead(user, false, false);
        log.debug("Unseen count for user {}: {}", email, count);
        return count;
    }

    public Map<NotificationType, Long> getNotificationCountsByType(String email) {
        User user = getUserByEmail(email);
        List<Object[]> results = notificationRepository.getNotificationCountsByType(user);

        return results.stream().collect(Collectors.toMap(
                result -> (NotificationType) result[0],
                result -> (Long) result[1]
        ));
    }

    // ======================== SOCIAL MEDIA EVENT HANDLERS ========================

    @Async
    public void handlePostLike(String postOwnerEmail, String likerEmail, Long postId) {
        if (!postOwnerEmail.equals(likerEmail)) {
            createSocialNotification(postOwnerEmail, likerEmail, NotificationType.LIKE, postId, "POST");
        }
    }

    @Async
    public void handleComment(String postOwnerEmail, String commenterEmail, Long postId, Long commentId) {
        if (!postOwnerEmail.equals(commenterEmail)) {
            createSocialNotification(postOwnerEmail, commenterEmail, NotificationType.COMMENT, commentId, "COMMENT");
        }
    }

    @Async
    public void handleCommentReply(String commentOwnerEmail, String replierEmail, Long commentId, Long replyId) {
        if (!commentOwnerEmail.equals(replierEmail)) {
            createSocialNotification(commentOwnerEmail, replierEmail, NotificationType.REPLY, replyId, "COMMENT");
        }
    }

    @Async
    public void handleFollow(String followedUserEmail, String followerEmail) {
        createSocialNotification(followedUserEmail, followerEmail, NotificationType.FOLLOW, null, "USER");
    }

    @Async
    public void handleCommentLike(String commentOwnerEmail, String likerEmail, Long commentId) {
        if (!commentOwnerEmail.equals(likerEmail)) {
            createSocialNotification(commentOwnerEmail, likerEmail, NotificationType.LIKE, commentId, "COMMENT");
        }
    }

    // ======================== REAL-TIME NOTIFICATIONS ========================

    private void sendRealTimeNotification(Notification notification) {
        try {
            NotificationDTO dto = convertToDTO(notification);
            String destination = "/topic/notifications/" + notification.getRecipient().getId();

            // Send individual notification
            messagingTemplate.convertAndSend(destination, dto);

            // Send updated counts
            Map<String, Long> counts = getNotificationCountsMap(notification.getRecipient().getEmail());
            messagingTemplate.convertAndSend(destination + "/counts", counts);

            log.debug("Real-time notification sent to {}", destination);
        } catch (Exception e) {
            log.error("Error sending real-time notification", e);
        }
    }

    private Map<String, Long> getNotificationCountsMap(String email) {
        Map<String, Long> counts = new HashMap<>();
        counts.put("unread", getUnreadCount(email));
        counts.put("unseen", getUnseenCount(email));
        return counts;
    }

    // ======================== UTILITY METHODS ========================

    private String determineEntityType(NotificationType type, Long entityId) {
        if (entityId == null) {
            return "USER";
        }

        return switch (type) {
            case LIKE, COMMENT -> "POST";
            case REPLY -> "COMMENT";
            case FOLLOW, UNFOLLOW -> "USER";
            default -> "POST";
        };
    }

    private Notification buildNotification(User recipient, NotificationBuilder builder) {
        User actor = builder.getActor();

        String title = generateTitle(builder.getType(), actor);
        String message = generateMessage(builder.getType(), actor, builder.getCustomMessage());
        String actionUrl = generateActionUrl(builder.getEntityType(), builder.getEntityId());
        String groupKey = generateGroupKey(builder.getType(), builder.getEntityId(), builder.getEntityType());

        return Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(builder.getType())
                .title(title)
                .message(message)
                .entityId(builder.getEntityId())
                .entityType(builder.getEntityType())
                .actionUrl(actionUrl)
                .priority(builder.getPriority())
                .groupKey(groupKey)
                .metadata(serializeMetadata(builder.getMetadata()))
                .expiresAt(builder.getExpiresAt())
                .build();
    }

    private String generateTitle(NotificationType type, User actor) {
        if (actor == null) {
            return type.name().replace("_", " ").toLowerCase();
        }

        return switch (type) {
            case LIKE -> "New Like";
            case COMMENT -> "New Comment";
            case REPLY -> "New Reply";
            case FOLLOW -> "New Follower";
            case MENTION -> "You were mentioned";
            case TAG -> "You were tagged";
            default -> "Notification";
        };
    }

    private String generateMessage(NotificationType type, User actor, String customMessage) {
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            return customMessage;
        }

        if (actor == null) {
            return getDefaultMessage(type);
        }

        return actor.getUsername() + " " + getDefaultMessage(type);
    }

    private String getDefaultMessage(NotificationType type) {
        return switch (type) {
            case LIKE -> "liked your post";
            case COMMENT -> "commented on your post";
            case REPLY -> "replied to your comment";
            case FOLLOW -> "started following you";
            case MENTION -> "mentioned you";
            case TAG -> "tagged you";
            default -> "sent you a notification";
        };
    }

    private String generateActionUrl(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }

        return switch (entityType.toUpperCase()) {
            case "POST" -> "/posts/" + entityId;
            case "COMMENT" -> "/comments/" + entityId;
            case "USER" -> "/users/" + entityId;
            default -> null;
        };
    }

    private String generateGroupKey(NotificationType type, Long entityId, String entityType) {
        if (entityId == null || entityType == null) {
            return null;
        }
        return type.name() + "_" + entityType + "_" + entityId;
    }

    private NotificationDTO convertToDTO(Notification notification) {
        NotificationDTO.UserSummaryDTO actorDTO = null;
        if (notification.getActor() != null) {
            User actor = notification.getActor();
            actorDTO = NotificationDTO.UserSummaryDTO.builder()
                    .id(actor.getId())
                    .username(actor.getUsername())
                    .avatar(actor.getAvatar())
                    .build();
        }

        return NotificationDTO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .priority(notification.getPriority())
                .isRead(notification.isRead())
                .isSeen(notification.isSeen())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .actionUrl(notification.getActionUrl())
                .entityId(notification.getEntityId())
                .entityType(notification.getEntityType())
                .groupKey(notification.getGroupKey())
                .actor(actorDTO)
                .metadata(notification.getMetadata())
                .timeAgo(calculateTimeAgo(notification.getCreatedAt()))
                .expiresAt(notification.getExpiresAt())
                .isExpired(notification.isExpired())
                .build();
    }

    private String calculateTimeAgo(Date createdAt) {
        LocalDateTime created = createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(created, now);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";

        long hours = ChronoUnit.HOURS.between(created, now);
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";

        long days = ChronoUnit.DAYS.between(created, now);
        if (days < 7) return days + " day" + (days == 1 ? "" : "s") + " ago";

        long weeks = days / 7;
        if (weeks < 4) return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";

        long months = ChronoUnit.MONTHS.between(created, now);
        return months + " month" + (months == 1 ? "" : "s") + " ago";
    }

    private boolean isDuplicateNotification(User recipient, NotificationBuilder builder) {
        if (builder.getEntityId() == null || builder.getEntityType() == null) {
            return false;
        }

        List<Notification> existing = notificationRepository
                .findByEntityIdAndEntityTypeAndType(builder.getEntityId(), builder.getEntityType(), builder.getType());

        return existing.stream()
                .anyMatch(n -> n.getRecipient().equals(recipient) &&
                        n.getActor() != null &&
                        n.getActor().equals(builder.getActor()));
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Error serializing notification metadata", e);
            return null;
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private Notification getNotificationById(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
    }

    // ======================== CLEANUP OPERATIONS ========================

    @Transactional
    public int cleanupExpiredNotifications() {
        return notificationRepository.deleteExpiredNotifications(new Date());
    }
}