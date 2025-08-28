package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.MessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {

    // Find read status for a message
    @Query("SELECT r FROM MessageReadStatus r " +
            "WHERE r.message.id = :messageId")
    List<MessageReadStatus> findByMessageId(@Param("messageId") Long messageId);

    // Find specific user's read status for a message
    @Query("SELECT r FROM MessageReadStatus r " +
            "WHERE r.message.id = :messageId AND r.user.id = :userId")
    Optional<MessageReadStatus> findByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    // Count how many users have read a message
    @Query("SELECT COUNT(r) FROM MessageReadStatus r " +
            "WHERE r.message.id = :messageId AND r.readAt IS NOT NULL")
    Long countReadByMessageId(@Param("messageId") Long messageId);

    // Find unread messages for a user in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId " +
            "AND m.sender.id != :userId " +
            "AND m.isDeleted = false " +
            "AND NOT EXISTS (SELECT r FROM MessageReadStatus r " +
            "WHERE r.message.id = m.id AND r.user.id = :userId AND r.readAt IS NOT NULL)")
    List<MessageReadStatus> findUnreadMessagesForUser(@Param("chatId") Long chatId, @Param("userId") Long userId);
}

