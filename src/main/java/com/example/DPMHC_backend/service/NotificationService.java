package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.NotificationDTO;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.CommentRepository;
import com.example.DPMHC_backend.repository.NotificationRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final CommentRepository commentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ======================== ENHANCED NOTIFICATION STATE MANAGEMENT ========================

    /**
     * Mark notification as read with cache eviction
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "notificationCounts", key = "'unread:' + #userEmail"),
        @CacheEvict(value = "notificationCounts", key = "'unseen:' + #userEmail"),
        @CacheEvict(value = "notificationCounts", key = "'byType:' + #userEmail"),
        @CacheEvict(value = "unreadNotifications", key = "#userEmail")
    })
    public NotificationDTO markAsReadAndReturn(Long notificationId, String userEmail) {
        log.debug("🗑️ Cache EVICT: Clearing notification caches for email: {}", userEmail);
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

    @WriteDB(type = WriteDB.OperationType.UPDATE)
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

    @WriteDB(type = WriteDB.OperationType.UPDATE)
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

    @WriteDB(type = WriteDB.OperationType.DELETE)
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

    @WriteDB(type = WriteDB.OperationType.UPDATE)
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

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @Async
    public void createNotification(NotificationBuilder builder) {
        try {
            User recipient = getUserByEmail(builder.getRecipientEmail());

            // ENHANCED: Use database-level duplicate checking with proper synchronization
            synchronized (this) {
                try {
                    // ENHANCED: Use database-level duplicate checking with proper locking
                    if (builder.isCheckDuplicates() && isDuplicateNotification(recipient, builder)) {
                        log.debug("Duplicate notification prevented for user {} - Type: {}, Entity: {}, Actor: {}", 
                                 recipient.getEmail(), builder.getType(), builder.getEntityId(), 
                                 builder.getActor() != null ? builder.getActor().getEmail() : "null");
                        return;
                    }

                    Notification notification = buildNotification(recipient, builder);
                    notification = notificationRepository.save(notification);

                    // Send real-time notification via WebSocket
                    sendRealTimeNotification(notification);

                    log.info("Notification created: {} for user {}", builder.getType(), recipient.getEmail());
                } catch (Exception e) {
                    log.error("Error in notification creation", e);
                }
            }
        } catch (Exception e) {
            log.error("Error creating notification", e);
        }
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
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

    /**
     * Create notification with User objects and priority
     */
    @Async
    public void createNotification(User recipient, NotificationType type, String message,
                                   Long entityId, Long actorId, NotificationPriority priority) {
        try {
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
                    .priority(priority)
                    .checkDuplicates(false) // For admin reports, we want all notifications
                    .generateContent(false) // Use custom message
                    .build();

            createNotification(builder);

            log.info("Admin notification created: {} from user {} to user {}", type, actorId, recipient.getId());
        } catch (Exception e) {
            log.error("Error creating admin notification for recipient {} from actor {}", recipient.getId(), actorId, e);
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

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    public Page<NotificationDTO> getNotifications(String email, Pageable pageable) {
        log.debug("Fetching notifications for user: {}, page: {}, size: {}",
                email, pageable.getPageNumber(), pageable.getPageSize());

        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientOrderByPriorityAndCreatedAt(user, pageable);

        log.debug("Found {} notifications for user: {}", notifications.getContent().size(), email);

        return notifications.map(this::convertToDTO);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    public Page<NotificationDTO> getUnreadNotifications(String email, Pageable pageable) {
        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientAndIsReadOrderByCreatedAtDesc(user, false, pageable);
        return notifications.map(this::convertToDTO);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    public Page<NotificationDTO> getNotificationsByType(String email, NotificationType type, Pageable pageable) {
        User user = getUserByEmail(email);
        Page<Notification> notifications = notificationRepository
                .findByRecipientAndTypeOrderByCreatedAtDesc(user, type, pageable);
        return notifications.map(this::convertToDTO);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
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

    // ======================== CACHED STATISTICS AND COUNTS ========================

    /**
     * CACHED: Get unread notification count with Redis caching (2min TTL)
     */
    @Cacheable(value = "notificationCounts", key = "'unread:' + #email", unless = "#result == null")
    public long getUnreadCount(String email) {
        log.debug("🔍 Cache MISS: Loading unread count for email: {}", email);
        User user = getUserByEmail(email);
        long count = notificationRepository.countByRecipientAndIsRead(user, false);
        log.debug("Unread count for user {}: {}", email, count);
        return count;
    }

    /**
     * CACHED: Get unseen notification count with Redis caching (2min TTL)
     */
    @Cacheable(value = "notificationCounts", key = "'unseen:' + #email", unless = "#result == null")
    public long getUnseenCount(String email) {
        log.debug("🔍 Cache MISS: Loading unseen count for email: {}", email);
        User user = getUserByEmail(email);
        long count = notificationRepository.countByRecipientAndIsSeenAndIsRead(user, false, false);
        log.debug("Unseen count for user {}: {}", email, count);
        return count;
    }

    /**
     * CACHED: Get notification counts by type with Redis caching (2min TTL)
     */
    @Cacheable(value = "notificationCounts", key = "'byType:' + #email", unless = "#result == null || #result.empty")
    public Map<NotificationType, Long> getNotificationCountsByType(String email) {
        log.debug("🔍 Cache MISS: Loading notification counts by type for email: {}", email);
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
            // For comment notifications, use postId as entityId and "POST" as entityType
            // This makes more sense semantically and helps with duplicate detection
            createSocialNotification(postOwnerEmail, commenterEmail, NotificationType.COMMENT, postId, "POST");
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
            // For comment likes, we need to navigate to the post, not the comment
            // So we need to get the post ID from the comment
            try {
                Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
                Long postId = comment.getPost().getId();
                
                // Create notification with post ID for navigation, but keep comment ID in metadata
                User actor = getUserByEmail(likerEmail);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("commentId", commentId);
                metadata.put("postId", postId);
                
                NotificationBuilder builder = NotificationBuilder.builder()
                        .recipientEmail(commentOwnerEmail)
                        .actor(actor)
                        .type(NotificationType.LIKE)
                        .entityId(postId)  // Use post ID for navigation
                        .entityType("COMMENT")  // But keep entity type as COMMENT for message
                        .priority(NotificationPriority.NORMAL)
                        .checkDuplicates(true)
                        .generateContent(true)
                        .metadata(metadata)
                        .build();

                createNotification(builder);
            } catch (Exception e) {
                log.error("Error handling comment like notification", e);
                // Fallback to original behavior
                createSocialNotification(commentOwnerEmail, likerEmail, NotificationType.LIKE, commentId, "COMMENT");
            }
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
        String message = generateMessage(builder.getType(), actor, builder.getCustomMessage(), builder.getEntityType());
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
            return getDefaultMessage(type, null);
        }

        return actor.getUsername() + " " + getDefaultMessage(type, null);
    }

    private String generateMessage(NotificationType type, User actor, String customMessage, String entityType) {
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            return customMessage;
        }

        if (actor == null) {
            return getDefaultMessage(type, entityType);
        }

        return actor.getUsername() + " " + getDefaultMessage(type, entityType);
    }

    private String getDefaultMessage(NotificationType type, String entityType) {
        return switch (type) {
            case LIKE -> {
                if ("COMMENT".equals(entityType)) {
                    yield "liked your comment";
                } else {
                    yield "liked your post";
                }
            }
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

        // For LIKE notifications, check if the same user already has a like notification for the same entity
        if (builder.getType() == NotificationType.LIKE) {
            // Check for any existing like notification from the same actor to the same recipient for the same entity
            List<Notification> existing = notificationRepository
                    .findByEntityIdAndEntityTypeAndType(builder.getEntityId(), builder.getEntityType(), builder.getType());
            
            boolean hasDuplicate = existing.stream()
                    .anyMatch(n -> n.getRecipient().equals(recipient) &&
                            n.getActor() != null &&
                            n.getActor().equals(builder.getActor()));
            
            if (hasDuplicate) {
                log.warn("DUPLICATE LIKE notification prevented: actor={}, recipient={}, entityId={}, entityType={}", 
                         builder.getActor() != null ? builder.getActor().getEmail() : "null", 
                         recipient.getEmail(), builder.getEntityId(), builder.getEntityType());
                return true;
            }
            
            // Additional check: Look for very recent like notifications (within last 10 seconds) to handle race conditions
            java.util.Date tenSecondsAgo = new java.util.Date(System.currentTimeMillis() - 10 * 1000);
            boolean hasRecentDuplicate = existing.stream()
                    .anyMatch(n -> n.getRecipient().equals(recipient) &&
                            n.getActor() != null &&
                            n.getActor().equals(builder.getActor()) &&
                            n.getCreatedAt().after(tenSecondsAgo));
            
            if (hasRecentDuplicate) {
                log.warn("RECENT DUPLICATE LIKE notification prevented (within 10 seconds): actor={}, recipient={}, entityId={}, entityType={}", 
                         builder.getActor() != null ? builder.getActor().getEmail() : "null", 
                         recipient.getEmail(), builder.getEntityId(), builder.getEntityType());
                return true;
            }
        }
        
        // For COMMENT notifications, check within the last 30 seconds to prevent spam/race conditions
        if (builder.getType() == NotificationType.COMMENT) {
            java.util.Date thirtySecondsAgo = new java.util.Date(System.currentTimeMillis() - 30 * 1000);
            
            List<Notification> recentComments = notificationRepository
                    .findByEntityIdAndEntityTypeAndType(builder.getEntityId(), builder.getEntityType(), builder.getType())
                    .stream()
                    .filter(n -> n.getRecipient().getId().equals(recipient.getId()) &&
                                n.getActor() != null &&
                                n.getActor().getId().equals(builder.getActor().getId()) &&
                                n.getCreatedAt().after(thirtySecondsAgo))
                    .toList();
            
            if (!recentComments.isEmpty()) {
                log.warn("Duplicate COMMENT notification prevented (within 30 seconds): actor={}, recipient={}, entityId={}, entityType={}", 
                         builder.getActor() != null ? builder.getActor().getEmail() : "null", 
                         recipient.getEmail(), builder.getEntityId(), builder.getEntityType());
                return true;
            }
        }
        
        // For other types (FOLLOW, REPLY, etc.), use the original logic
        List<Notification> existing = notificationRepository
                .findByEntityIdAndEntityTypeAndType(builder.getEntityId(), builder.getEntityType(), builder.getType());

        boolean hasDuplicate = existing.stream()
                .anyMatch(n -> n.getRecipient().getId().equals(recipient.getId()) &&
                        n.getActor() != null &&
                        n.getActor().getId().equals(builder.getActor().getId()));
        
        if (hasDuplicate) {
            log.debug("Duplicate {} notification prevented: actor={}, recipient={}, entityId={}, entityType={}", 
                     builder.getType(), builder.getActor() != null ? builder.getActor().getEmail() : "null", 
                     recipient.getEmail(), builder.getEntityId(), builder.getEntityType());
        }
        
        return hasDuplicate;
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

    @Transactional
    public int cleanupDuplicateNotifications() {
        log.info("Starting cleanup of duplicate notifications");
        
        int totalCleaned = 0;
        
        try {
            // Clean up duplicate LIKE notifications
            totalCleaned += cleanupDuplicateLikeNotifications();
            
            log.info("Duplicate notification cleanup completed. Total cleaned: {}", totalCleaned);
            return totalCleaned;
            
        } catch (Exception e) {
            log.error("Error during duplicate notification cleanup", e);
            return 0;
        }
    }
    
    @Transactional
    public int cleanupDuplicateLikeNotifications() {
        log.info("Cleaning up duplicate LIKE notifications");
        
        try {
            // Find all like notifications grouped by recipient, actor, entityId, and entityType
            List<Notification> allLikeNotifications = notificationRepository
                    .findByTypeOrderByCreatedAtDesc(NotificationType.LIKE);
            
            Map<String, List<Notification>> groupedNotifications = allLikeNotifications.stream()
                    .collect(Collectors.groupingBy(n -> {
                        return n.getRecipient().getId() + "_" + 
                               (n.getActor() != null ? n.getActor().getId() : "null") + "_" + 
                               n.getEntityId() + "_" + 
                               n.getEntityType();
                    }));
            
            int duplicatesRemoved = 0;
            
            for (Map.Entry<String, List<Notification>> entry : groupedNotifications.entrySet()) {
                List<Notification> duplicates = entry.getValue();
                
                if (duplicates.size() > 1) {
                    // Keep the most recent notification, delete the rest
                    duplicates.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                    
                    for (int i = 1; i < duplicates.size(); i++) {
                        Notification toDelete = duplicates.get(i);
                        log.debug("Deleting duplicate LIKE notification: id={}, recipient={}, actor={}, entityId={}", 
                                 toDelete.getId(), toDelete.getRecipient().getEmail(), 
                                 toDelete.getActor() != null ? toDelete.getActor().getEmail() : "null",
                                 toDelete.getEntityId());
                        
                        notificationRepository.delete(toDelete);
                        duplicatesRemoved++;
                    }
                }
            }
            
            log.info("Removed {} duplicate LIKE notifications", duplicatesRemoved);
            return duplicatesRemoved;
            
        } catch (Exception e) {
            log.error("Error cleaning up duplicate LIKE notifications", e);
            return 0;
        }
    }
}