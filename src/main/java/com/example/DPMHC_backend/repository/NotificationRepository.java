package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Notification;
import com.example.DPMHC_backend.model.NotificationPriority;
import com.example.DPMHC_backend.model.NotificationType;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Basic queries
    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndIsReadOrderByCreatedAtDesc(User recipient, boolean isRead, Pageable pageable);

    List<Notification> findByRecipientAndIsReadOrderByCreatedAtDesc(User recipient, boolean isRead);

    // Count queries for badges/indicators
    long countByRecipientAndIsRead(User recipient, boolean isRead);

    long countByRecipientAndIsSeenAndIsRead(User recipient, boolean isSeen, boolean isRead);

    // Type-based filtering
    Page<Notification> findByRecipientAndTypeOrderByCreatedAtDesc(User recipient, NotificationType type, Pageable pageable);

    Page<Notification> findByRecipientAndTypeInOrderByCreatedAtDesc(User recipient, List<NotificationType> types, Pageable pageable);

    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);

    // Priority-based queries
    Page<Notification> findByRecipientAndPriorityOrderByCreatedAtDesc(User recipient, NotificationPriority priority, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient ORDER BY n.priority DESC, n.createdAt DESC")
    Page<Notification> findByRecipientOrderByPriorityAndCreatedAt(@Param("recipient") User recipient, Pageable pageable);

    // Entity-specific queries
    List<Notification> findByEntityIdAndEntityTypeAndType(Long entityId, String entityType, NotificationType type);

    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient AND n.entityId = :entityId AND n.entityType = :entityType")
    List<Notification> findByRecipientAndEntity(@Param("recipient") User recipient, @Param("entityId") Long entityId, @Param("entityType") String entityType);

    // Grouping queries
    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient AND n.groupKey = :groupKey ORDER BY n.createdAt DESC")
    List<Notification> findByRecipientAndGroupKey(@Param("recipient") User recipient, @Param("groupKey") String groupKey);

    // Bulk operations
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipient = :recipient AND n.isRead = false")
    int markAllAsReadForUser(@Param("recipient") User recipient, @Param("readAt") Date readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.isSeen = true WHERE n.recipient = :recipient AND n.isSeen = false")
    int markAllAsSeenForUser(@Param("recipient") User recipient);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id IN :ids")
    int markNotificationsAsRead(@Param("ids") List<Long> ids, @Param("readAt") Date readAt);

    // Cleanup queries
    @Query("SELECT n FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :now")
    List<Notification> findExpiredNotifications(@Param("now") Date now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :now")
    int deleteExpiredNotifications(@Param("now") Date now);

    // Date range queries
    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient AND n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientAndDateRange(@Param("recipient") User recipient,
                                                  @Param("startDate") Date startDate,
                                                  @Param("endDate") Date endDate,
                                                  Pageable pageable);

    // Statistics queries
    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.recipient = :recipient GROUP BY n.type")
    List<Object[]> getNotificationCountsByType(@Param("recipient") User recipient);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.createdAt >= :since")
    long countNotificationsSince(@Param("recipient") User recipient, @Param("since") Date since);

    // Actor-based queries
    Page<Notification> findByRecipientAndActorOrderByCreatedAtDesc(User recipient, User actor, Pageable pageable);

    @Query("SELECT DISTINCT n.actor FROM Notification n WHERE n.recipient = :recipient AND n.actor IS NOT NULL")
    List<User> findDistinctActorsByRecipient(@Param("recipient") User recipient);

    // Cleanup duplicate notifications
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN (" +
           "SELECT n2.id FROM Notification n2 WHERE n2.recipient = :recipient " +
           "AND n2.type = :type AND n2.entityId = :entityId AND n2.entityType = :entityType " +
           "AND n2.actor = :actor AND n2.id != (" +
           "SELECT MIN(n3.id) FROM Notification n3 WHERE n3.recipient = :recipient " +
           "AND n3.type = :type AND n3.entityId = :entityId AND n3.entityType = :entityType " +
           "AND n3.actor = :actor))")
    int deleteDuplicateNotifications(@Param("recipient") User recipient, 
                                   @Param("type") NotificationType type,
                                   @Param("entityId") Long entityId, 
                                   @Param("entityType") String entityType,
                                   @Param("actor") User actor);
}
