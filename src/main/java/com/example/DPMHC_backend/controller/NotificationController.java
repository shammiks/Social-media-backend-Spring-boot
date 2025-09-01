package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.NotificationDTO;
import com.example.DPMHC_backend.model.NotificationType;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    // ======================== BASIC NOTIFICATION OPERATIONS ========================

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getUserNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching notifications for user: {}, page: {}, size: {}", user.getEmail(), page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDTO> notifications = notificationService.getNotifications(user.getEmail(), pageable);
        log.debug("Returned {} notifications for user: {}", notifications.getContent().size(), user.getEmail());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    public ResponseEntity<Page<NotificationDTO>> getUnreadNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching unread notifications for user: {}", user.getEmail());
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDTO> notifications = notificationService.getUnreadNotifications(user.getEmail(), pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<Page<NotificationDTO>> getNotificationsByType(
            @AuthenticationPrincipal User user,
            @PathVariable NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDTO> notifications = notificationService.getNotificationsByType(user.getEmail(), type, pageable);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/filter")
    public ResponseEntity<Page<NotificationDTO>> getNotificationsByTypes(
            @AuthenticationPrincipal User user,
            @RequestBody NotificationFilterRequest request) {

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<NotificationDTO> notifications = notificationService.getNotificationsByTypes(
                user.getEmail(), request.getTypes(), pageable);
        return ResponseEntity.ok(notifications);
    }

    // ======================== ENHANCED NOTIFICATION STATE MANAGEMENT ========================

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        log.debug("Marking notification {} as read for user: {}", id, user.getEmail());

        try {
            // Mark as read and get updated notification
            NotificationDTO updatedNotification = notificationService.markAsReadAndReturn(id, user.getEmail());

            // Get updated counts
            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            log.info("Successfully marked notification {} as read. New unread count: {}", id, unreadCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification marked as read",
                    "notification", updatedNotification,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark notification {} as read for user {}: {}", id, user.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark notification as read: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/unread")
    public ResponseEntity<Map<String, Object>> markNotificationAsUnread(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        log.debug("Marking notification {} as unread for user: {}", id, user.getEmail());

        try {
            NotificationDTO updatedNotification = notificationService.markAsUnreadAndReturn(id, user.getEmail());

            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification marked as unread",
                    "notification", updatedNotification,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark notification {} as unread: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark notification as unread: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/seen")
    public ResponseEntity<Map<String, Object>> markNotificationAsSeen(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        log.debug("Marking notification {} as seen for user: {}", id, user.getEmail());

        try {
            NotificationDTO updatedNotification = notificationService.markAsSeenAndReturn(id, user.getEmail());

            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification marked as seen",
                    "notification", updatedNotification,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark notification {} as seen: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark notification as seen: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        log.debug("Deleting notification {} for user: {}", id, user.getEmail());

        try {
            notificationService.deleteNotificationForUser(id, user.getEmail());

            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification deleted successfully",
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to delete notification {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to delete notification: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal User user) {
        log.debug("Marking all notifications as read for user: {}", user.getEmail());

        try {
            int updatedCount = notificationService.markAllAsRead(user.getEmail());
            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            log.info("Marked {} notifications as read for user: {}. New unread count: {}",
                    updatedCount, user.getEmail(), unreadCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All notifications marked as read",
                    "updatedCount", updatedCount,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark all as read for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark all as read: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/mark-all-seen")
    public ResponseEntity<Map<String, Object>> markAllAsSeen(@AuthenticationPrincipal User user) {
        log.debug("Marking all notifications as seen for user: {}", user.getEmail());

        try {
            int updatedCount = notificationService.markAllAsSeen(user.getEmail());
            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All notifications marked as seen",
                    "updatedCount", updatedCount,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark all as seen for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark all as seen: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/mark-multiple-read")
    public ResponseEntity<Map<String, Object>> markMultipleAsRead(
            @RequestBody MarkMultipleRequest request,
            @AuthenticationPrincipal User user) {

        log.debug("Marking {} notifications as read for user: {}",
                request.getNotificationIds().size(), user.getEmail());

        try {
            int updatedCount = notificationService.markMultipleAsReadForUser(
                    request.getNotificationIds(), user.getEmail());

            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notifications marked as read",
                    "updatedCount", updatedCount,
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount
            ));

        } catch (Exception e) {
            log.error("Failed to mark multiple as read: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark notifications as read: " + e.getMessage()
            ));
        }
    }

    // ======================== STATISTICS AND COUNTS ========================

    @GetMapping("/counts")
    public ResponseEntity<NotificationCounts> getNotificationCounts(@AuthenticationPrincipal User user) {
        log.debug("Fetching notification counts for user: {}", user.getEmail());

        long unreadCount = notificationService.getUnreadCount(user.getEmail());
        long unseenCount = notificationService.getUnseenCount(user.getEmail());

        log.debug("Notification counts for user {}: unread={}, unseen={}",
                user.getEmail(), unreadCount, unseenCount);

        NotificationCounts counts = NotificationCounts.builder()
                .unreadCount(unreadCount)
                .unseenCount(unseenCount)
                .build();

        return ResponseEntity.ok(counts);
    }

    @GetMapping("/counts/by-type")
    public ResponseEntity<Map<NotificationType, Long>> getNotificationCountsByType(
            @AuthenticationPrincipal User user) {

        Map<NotificationType, Long> counts = notificationService.getNotificationCountsByType(user.getEmail());
        return ResponseEntity.ok(counts);
    }

    // ======================== REAL-TIME ENDPOINTS ========================

    @GetMapping("/recent")
    public ResponseEntity<Page<NotificationDTO>> getRecentNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "5") int limit) {

        Pageable pageable = PageRequest.of(0, limit);
        Page<NotificationDTO> notifications = notificationService.getNotifications(user.getEmail(), pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/badge-count")
    public ResponseEntity<Map<String, Long>> getBadgeCount(@AuthenticationPrincipal User user) {
        long unreadCount = notificationService.getUnreadCount(user.getEmail());
        return ResponseEntity.ok(Map.of("count", unreadCount));
    }

    // ======================== HEALTH CHECK ENDPOINT ========================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(@AuthenticationPrincipal User user) {
        try {
            long unreadCount = notificationService.getUnreadCount(user.getEmail());
            long unseenCount = notificationService.getUnseenCount(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "userId", user.getId(),
                    "userEmail", user.getEmail(),
                    "unreadCount", unreadCount,
                    "unseenCount", unseenCount,
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Health check failed for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ======================== ADMIN ENDPOINTS ========================

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredNotifications() {
        try {
            int deletedCount = notificationService.cleanupExpiredNotifications();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Expired notifications cleaned up",
                    "deletedCount", deletedCount
            ));
        } catch (Exception e) {
            log.error("Failed to cleanup expired notifications: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to cleanup expired notifications: " + e.getMessage()
            ));
        }
    }

    // ======================== REQUEST/RESPONSE DTOs ========================

    @Data
    public static class NotificationFilterRequest {
        private List<NotificationType> types;
        private int page = 0;
        private int size = 20;
    }

    @Data
    public static class MarkMultipleRequest {
        private List<Long> notificationIds;
    }

    @Data
    @lombok.Builder
    public static class NotificationCounts {
        private long unreadCount;
        private long unseenCount;
    }
}