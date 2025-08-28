package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Chat;
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
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // Find chats where user is a participant
    @Query("SELECT DISTINCT c FROM Chat c " +
            "JOIN c.participants p " +
            "WHERE p.user.id = :userId AND p.isActive = true AND c.isActive = true " +
            "ORDER BY c.lastMessageAt DESC")
    List<Chat> findChatsByUserId(@Param("userId") Long userId);

    // Find chats with pagination
    @Query("SELECT DISTINCT c FROM Chat c " +
            "JOIN c.participants p " +
            "WHERE p.user.id = :userId AND p.isActive = true AND c.isActive = true " +
            "ORDER BY c.lastMessageAt DESC")
    Page<Chat> findChatsByUserId(@Param("userId") Long userId, Pageable pageable);

    // Find private chat between two users
    @Query("SELECT c FROM Chat c " +
            "JOIN c.participants p1 " +
            "JOIN c.participants p2 " +
            "WHERE c.chatType = 'PRIVATE' " +
            "AND p1.user.id = :user1Id AND p1.isActive = true " +
            "AND p2.user.id = :user2Id AND p2.isActive = true " +
            "AND c.isActive = true")
    Optional<Chat> findPrivateChatBetweenUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    // Check if user is participant of a chat
    @Query("SELECT COUNT(p) > 0 FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.user.id = :userId AND p.isActive = true")
    boolean isUserParticipantOfChat(@Param("chatId") Long chatId, @Param("userId") Long userId);

    // Find active chats by name (for search)
    @Query("SELECT DISTINCT c FROM Chat c " +
            "JOIN c.participants p " +
            "WHERE p.user.id = :userId AND p.isActive = true " +
            "AND c.isActive = true " +
            "AND (LOWER(c.chatName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR EXISTS (SELECT p2 FROM c.participants p2 JOIN p2.user u " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')))) " +
            "ORDER BY c.lastMessageAt DESC")
    List<Chat> searchChatsByName(@Param("userId") Long userId, @Param("searchTerm") String searchTerm);

    // Find chats updated after specific time (for real-time sync)
    @Query("SELECT DISTINCT c FROM Chat c " +
            "JOIN c.participants p " +
            "WHERE p.user.id = :userId AND p.isActive = true " +
            "AND c.isActive = true " +
            "AND c.lastMessageAt > :lastSync " +
            "ORDER BY c.lastMessageAt DESC")
    List<Chat> findChatsUpdatedAfter(@Param("userId") Long userId, @Param("lastSync") LocalDateTime lastSync);

    // Count unread chats for user
    @Query("SELECT COUNT(DISTINCT c) FROM Chat c " +
            "JOIN c.participants p " +
            "JOIN c.messages m " +
            "WHERE p.user.id = :userId AND p.isActive = true " +
            "AND c.isActive = true " +
            "AND m.createdAt > COALESCE(p.lastSeenAt, c.createdAt) " +
            "AND m.sender.id != :userId")
    Long countUnreadChatsForUser(@Param("userId") Long userId);
}
