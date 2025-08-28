package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.ChatParticipant;
import com.example.DPMHC_backend.model.MessageReaction;
import com.example.DPMHC_backend.model.MessageReadStatus;
import com.example.DPMHC_backend.model.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// ==================== Chat Participant Repository ====================
@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    // Find participants of a chat
    @Query("SELECT p FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.isActive = true")
    List<ChatParticipant> findByChatIdAndActive(@Param("chatId") Long chatId);

    // Find specific participant
    @Query("SELECT p FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.user.id = :userId AND p.isActive = true")
    Optional<ChatParticipant> findByChatIdAndUserId(@Param("chatId") Long chatId, @Param("userId") Long userId);

    // Find user's chats
    @Query("SELECT p FROM ChatParticipant p " +
            "WHERE p.user.id = :userId AND p.isActive = true")
    List<ChatParticipant> findByUserIdAndActive(@Param("userId") Long userId);

    // Count active participants in a chat
    @Query("SELECT COUNT(p) FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.isActive = true")
    Long countActiveByChatId(@Param("chatId") Long chatId);

    // Find admins and owners of a chat
    @Query("SELECT p FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.isActive = true " +
            "AND p.role IN ('ADMIN', 'OWNER')")
    List<ChatParticipant> findAdminsAndOwnersByChatId(@Param("chatId") Long chatId);

    // Check if user is admin or owner
    @Query("SELECT COUNT(p) > 0 FROM ChatParticipant p " +
            "WHERE p.chat.id = :chatId AND p.user.id = :userId " +
            "AND p.isActive = true AND p.role IN ('ADMIN', 'OWNER')")
    boolean isUserAdminOrOwner(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
