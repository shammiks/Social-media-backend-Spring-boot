package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    // Find reactions for a message
    @Query("SELECT r FROM MessageReaction r " +
            "WHERE r.message.id = :messageId")
    List<MessageReaction> findByMessageId(@Param("messageId") Long messageId);

    // Find specific user's reaction to a message
    @Query("SELECT r FROM MessageReaction r " +
            "WHERE r.message.id = :messageId AND r.user.id = :userId AND r.emoji = :emoji")
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(@Param("messageId") Long messageId,
                                                               @Param("userId") Long userId,
                                                               @Param("emoji") String emoji);

    // Count reactions by emoji for a message
    @Query("SELECT r.emoji, COUNT(r) FROM MessageReaction r " +
            "WHERE r.message.id = :messageId " +
            "GROUP BY r.emoji")
    List<Object[]> countReactionsByEmoji(@Param("messageId") Long messageId);

    // Find all reactions by a user in a chat
    @Query("SELECT r FROM MessageReaction r " +
            "WHERE r.user.id = :userId AND r.message.chat.id = :chatId")
    List<MessageReaction> findByUserIdAndChatId(@Param("userId") Long userId, @Param("chatId") Long chatId);

    // Delete user's reaction to a message with specific emoji
    @Query("DELETE FROM MessageReaction r " +
            "WHERE r.message.id = :messageId AND r.user.id = :userId AND r.emoji = :emoji")
    void deleteByMessageIdAndUserIdAndEmoji(@Param("messageId") Long messageId,
                                            @Param("userId") Long userId,
                                            @Param("emoji") String emoji);
}

