package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.UserBlockDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.UserBlock;
import com.example.DPMHC_backend.repository.UserBlockRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    /**
     * Block a user
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "user-blocks", key = "#blockerId + ':blocked:' + #blockedUserId"),
        @CacheEvict(value = "user-mutual-blocks", key = "'mutual:' + #blockerId + ':' + #blockedUserId"),
        @CacheEvict(value = "user-mutual-blocks", key = "'mutual:' + #blockedUserId + ':' + #blockerId"),
        @CacheEvict(value = "user-blocked-lists", key = "#blockerId"),
        @CacheEvict(value = "user-blocked-ids", key = "#blockerId")
    })
    public UserBlockDTO blockUser(Long blockerId, Long blockedUserId) {
        log.info("User {} blocking user {}", blockerId, blockedUserId);

        // Validation
        if (blockerId.equals(blockedUserId)) {
            throw new RuntimeException("Cannot block yourself");
        }

        // Check if users exist
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new RuntimeException("Blocker user not found"));

        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new RuntimeException("User to block not found"));

        // Check if already actively blocked
        Optional<UserBlock> existingActiveBlock = userBlockRepository.findActiveBlock(blockerId, blockedUserId);
        if (existingActiveBlock.isPresent()) {
            throw new RuntimeException("User is already blocked");
        }

        // Check if there's an inactive block record (previously unblocked)
        Optional<UserBlock> existingInactiveBlock = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedUserId);
        
        UserBlock userBlock;
        if (existingInactiveBlock.isPresent()) {
            // Reactivate existing block
            userBlock = existingInactiveBlock.get();
            userBlock.setIsActive(true);
            userBlock.setBlockedAt(LocalDateTime.now()); // Update the blocked time
            log.info("Reactivating existing block record for user {} blocking user {}", blockerId, blockedUserId);
        } else {
            // Create new block
            userBlock = UserBlock.builder()
                    .blocker(blocker)
                    .blocked(blocked)
                    .isActive(true)
                    .build();
            log.info("Creating new block record for user {} blocking user {}", blockerId, blockedUserId);
        }

        UserBlock savedBlock = userBlockRepository.save(userBlock);
        
        log.info("User {} successfully blocked user {}", blockerId, blockedUserId);
        return new UserBlockDTO(savedBlock);
    }

    /**
     * Unblock a user
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "user-blocks", key = "#blockerId + ':blocked:' + #blockedUserId"),
        @CacheEvict(value = "user-mutual-blocks", key = "'mutual:' + #blockerId + ':' + #blockedUserId"),
        @CacheEvict(value = "user-mutual-blocks", key = "'mutual:' + #blockedUserId + ':' + #blockerId"),
        @CacheEvict(value = "user-blocked-lists", key = "#blockerId"),
        @CacheEvict(value = "user-blocked-ids", key = "#blockerId")
    })
    public void unblockUser(Long blockerId, Long blockedUserId) {
        log.info("User {} unblocking user {}", blockerId, blockedUserId);

        UserBlock userBlock = userBlockRepository.findActiveBlock(blockerId, blockedUserId)
                .orElseThrow(() -> new RuntimeException("No active block found"));

        userBlock.unblock();
        userBlockRepository.save(userBlock);
        
        log.info("User {} successfully unblocked user {}", blockerId, blockedUserId);
    }

    /**
     * Check if user A has blocked user B
     */
    @Cacheable(value = "user-blocks", key = "#blockerId + ':blocked:' + #blockedUserId", unless = "#result == null")
    public boolean isUserBlocked(Long blockerId, Long blockedUserId) {
        log.debug("🚫 Checking if user {} blocked user {}", blockerId, blockedUserId);
        boolean result = userBlockRepository.isUserBlocked(blockerId, blockedUserId);
        log.debug("✅ User {} block check for user {}: {}", blockerId, blockedUserId, result);
        return result;
    }

    /**
     * Check if there's any block between two users (either way)
     */
    @Cacheable(value = "user-mutual-blocks", key = "'mutual:' + #userId1 + ':' + #userId2", unless = "#result == null")
    public boolean areUsersBlocked(Long userId1, Long userId2) {
        log.debug("🚫 Checking mutual blocks between users {} and {}", userId1, userId2);
        boolean result = userBlockRepository.areUsersBlocked(userId1, userId2);
        log.debug("✅ Mutual block check for users {} and {}: {}", userId1, userId2, result);
        return result;
    }

    /**
     * Get all users blocked by a specific user
     */
    @Cacheable(value = "user-blocked-lists", key = "#blockerId", unless = "#result == null || #result.isEmpty()")
    public List<UserBlockDTO> getBlockedUsers(Long blockerId) {
        log.debug("📋 Fetching blocked users list for user {}", blockerId);
        List<UserBlock> blocks = userBlockRepository.findActiveBlocksByBlocker(blockerId);
        List<UserBlockDTO> result = blocks.stream()
                .map(UserBlockDTO::new)
                .collect(Collectors.toList());
        log.debug("✅ Retrieved {} blocked users for user {}", result.size(), blockerId);
        return result;
    }

    /**
     * Get blocked users as a simple list of user IDs (for performance)
     */
    @Cacheable(value = "user-blocked-ids", key = "#blockerId", unless = "#result == null || #result.isEmpty()")
    public List<Long> getBlockedUserIds(Long blockerId) {
        log.debug("🔢 Fetching blocked user IDs for user {}", blockerId);
        List<Long> result = userBlockRepository.getBlockedUsers(blockerId)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
        log.debug("✅ Retrieved {} blocked user IDs for user {}", result.size(), blockerId);
        return result;
    }

    /**
     * Check if a specific user is blocked by the current user
     */
    @Cacheable(value = "user-blocks", key = "#blockerId + ':blocked:' + #targetUserId", unless = "#result == null")
    public boolean isSpecificUserBlocked(Long blockerId, Long targetUserId) {
        log.debug("🎯 Checking specific block: user {} -> user {}", blockerId, targetUserId);
        boolean result = userBlockRepository.isUserBlocked(blockerId, targetUserId);
        log.debug("✅ Specific block check for users {} -> {}: {}", blockerId, targetUserId, result);
        return result;
    }
}
