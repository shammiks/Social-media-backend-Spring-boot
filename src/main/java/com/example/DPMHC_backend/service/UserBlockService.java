package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.UserBlockDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.UserBlock;
import com.example.DPMHC_backend.repository.UserBlockRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public boolean isUserBlocked(Long blockerId, Long blockedUserId) {
        return userBlockRepository.isUserBlocked(blockerId, blockedUserId);
    }

    /**
     * Check if there's any block between two users (either way)
     */
    public boolean areUsersBlocked(Long userId1, Long userId2) {
        return userBlockRepository.areUsersBlocked(userId1, userId2);
    }

    /**
     * Get all users blocked by a specific user
     */
    public List<UserBlockDTO> getBlockedUsers(Long blockerId) {
        List<UserBlock> blocks = userBlockRepository.findActiveBlocksByBlocker(blockerId);
        return blocks.stream()
                .map(UserBlockDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Get blocked users as a simple list of user IDs (for performance)
     */
    public List<Long> getBlockedUserIds(Long blockerId) {
        return userBlockRepository.getBlockedUsers(blockerId)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }

    /**
     * Check if a specific user is blocked by the current user
     */
    public boolean isSpecificUserBlocked(Long blockerId, Long targetUserId) {
        return userBlockRepository.isUserBlocked(blockerId, targetUserId);
    }
}
