package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Find messages in a chat with pagination
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findMessagesByChatId(@Param("chatId") Long chatId, Pageable pageable);

    // Find messages in a chat (list)
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findMessagesByChatIdOrderByCreatedAt(@Param("chatId") Long chatId);

    // Find latest message in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC " +
            "LIMIT 1")
    Optional<Message> findLatestMessageInChat(@Param("chatId") Long chatId);

    // Search messages in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "AND (LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(m.sender.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesInChat(@Param("chatId") Long chatId,
                                       @Param("searchTerm") String searchTerm,
                                       Pageable pageable);

    // Find messages after specific time (for real-time sync)
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "AND m.createdAt > :afterTime " +
            "ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfterTime(@Param("chatId") Long chatId, @Param("afterTime") LocalDateTime afterTime);

    // Count unread messages for user in a chat
    @Query("SELECT COUNT(m) FROM Message m " +
            "JOIN m.chat c " +
            "JOIN c.participants p " +
            "WHERE m.chat.id = :chatId " +
            "AND p.user.id = :userId AND p.isActive = true " +
            "AND m.isDeleted = false " +
            "AND m.sender.id != :userId " +
            "AND m.createdAt > COALESCE(p.lastSeenAt, c.createdAt)")
    Long countUnreadMessagesForUserInChat(@Param("chatId") Long chatId, @Param("userId") Long userId);

    // Find pinned messages in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isPinned = true AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    List<Message> findPinnedMessagesInChat(@Param("chatId") Long chatId);

    // Find messages by sender in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.sender.id = :senderId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findMessagesBySenderInChat(@Param("chatId") Long chatId,
                                             @Param("senderId") Long senderId,
                                             Pageable pageable);

    // Find media messages in a chat
    @Query("SELECT m FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "AND m.messageType IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT') " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findMediaMessagesInChat(@Param("chatId") Long chatId, Pageable pageable);

    // Count total messages in a chat
    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false")
    Long countMessagesInChat(@Param("chatId") Long chatId);

    // Find messages with reactions
    @Query("SELECT DISTINCT m FROM Message m " +
            "JOIN m.reactions r " +
            "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findMessagesWithReactionsInChat(@Param("chatId") Long chatId, Pageable pageable);

    // Find reply messages (messages that are replies to other messages)
    @Query("SELECT m FROM Message m " +
            "WHERE m.replyToId = :messageId AND m.isDeleted = false " +
            "ORDER BY m.createdAt ASC")
    List<Message> findRepliestoMessage(@Param("messageId") Long messageId);
}
