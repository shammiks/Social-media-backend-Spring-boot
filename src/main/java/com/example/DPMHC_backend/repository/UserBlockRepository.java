package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    // Find active block between two users
    @Query("SELECT ub FROM UserBlock ub WHERE ub.blocker.id = :blockerId AND ub.blocked.id = :blockedId AND ub.isActive = true")
    Optional<UserBlock> findActiveBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    // Check if user A has blocked user B
    @Query("SELECT COUNT(ub) > 0 FROM UserBlock ub WHERE ub.blocker.id = :blockerId AND ub.blocked.id = :blockedId AND ub.isActive = true")
    boolean isUserBlocked(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    // Check if there's any active block between two users (either way)
    @Query("SELECT COUNT(ub) > 0 FROM UserBlock ub WHERE " +
           "((ub.blocker.id = :userId1 AND ub.blocked.id = :userId2) OR " +
           "(ub.blocker.id = :userId2 AND ub.blocked.id = :userId1)) AND ub.isActive = true")
    boolean areUsersBlocked(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // Get all users blocked by a specific user
    @Query("SELECT ub.blocked FROM UserBlock ub WHERE ub.blocker.id = :blockerId AND ub.isActive = true")
    List<User> getBlockedUsers(@Param("blockerId") Long blockerId);

    // Get all users who have blocked a specific user
    @Query("SELECT ub.blocker FROM UserBlock ub WHERE ub.blocked.id = :blockedId AND ub.isActive = true")
    List<User> getUsersWhoBlocked(@Param("blockedId") Long blockedId);

    // Get all active blocks by a user
    @Query("SELECT ub FROM UserBlock ub WHERE ub.blocker.id = :blockerId AND ub.isActive = true")
    List<UserBlock> findActiveBlocksByBlocker(@Param("blockerId") Long blockerId);
}
