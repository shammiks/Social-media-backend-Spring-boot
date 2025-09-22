package com.example.DPMHC_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduledTaskService {

    private final NotificationService notificationService;

    // Clean up expired notifications every hour
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void cleanupExpiredNotifications() {
        try {
            int deletedCount = notificationService.cleanupExpiredNotifications();
            if (deletedCount > 0) {
                log.info("Cleaned up {} expired notifications", deletedCount);
            }
        } catch (Exception e) {
            log.warn("Error during notification cleanup: {}. This may be due to ShardingSphere table detection during startup.", e.getMessage());
        }
    }

    // Clean up old read notifications (older than 30 days) every day at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldReadNotifications() {
        try {
            // This could be implemented to clean up old read notifications
            // to prevent database bloat
            log.info("Starting cleanup of old read notifications");
            // Implementation would go here
        } catch (Exception e) {
            log.error("Error during old notification cleanup", e);
        }
    }
}
