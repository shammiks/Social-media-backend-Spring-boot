package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.FollowDTO;
import com.example.DPMHC_backend.dto.FollowStatusDTO;
import com.example.DPMHC_backend.dto.UserDTO;
import com.example.DPMHC_backend.exception.SelfFollowException;
import com.example.DPMHC_backend.exception.UserNotFoundException;
import com.example.DPMHC_backend.model.Follow;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.FollowRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    // ========== ENHANCED CORE FOLLOW OPERATIONS ==========

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @CacheEvict(value = "followStatus", key = "{#followerId, #followeeId}")
    public FollowStatusDTO toggleFollow(Long followerId, Long followeeId) {
        log.info("Toggle follow request: follower={}, followee={}", followerId, followeeId);

        // Validation
        validateUsers(followerId, followeeId);

        Optional<Follow> existingFollow = followRepository
                .findByFollowerIdAndFolloweeId(followerId, followeeId);

        FollowStatusDTO result;
        if (existingFollow.isPresent()) {
            log.info("Unfollowing user: follower={}, followee={}", followerId, followeeId);
            result = unfollowUser(followerId, followeeId);
        } else {
            log.info("Following user: follower={}, followee={}", followerId, followeeId);
            result = followUser(followerId, followeeId);
        }

        log.info("Toggle follow result: {}", result);
        return result;
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @CacheEvict(value = "followStatus", key = "{#followerId, #followeeId}")
    public FollowStatusDTO followUser(Long followerId, Long followeeId) {
        log.info("Follow user request: follower={}, followee={}", followerId, followeeId);

        // Validation
        validateUsers(followerId, followeeId);

        // Check if already following
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.warn("User {} already follows user {}", followerId, followeeId);
            long followersCount = followRepository.countByFolloweeId(followeeId);
            return FollowStatusDTO.builder()
                    .isFollowing(true)
                    .following(true) // Add this field for compatibility
                    .followersCount(followersCount)
                    .message("Already following this user")
                    .build();
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new UserNotFoundException("Follower not found"));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new UserNotFoundException("Followee not found"));

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowee(followee);
        followRepository.save(follow);

        long followersCount = followRepository.countByFolloweeId(followeeId);

        FollowStatusDTO result = FollowStatusDTO.builder()
                .isFollowing(true)
                .following(true) // Add this field for compatibility
                .followersCount(followersCount)
                .message("Successfully followed user")
                .build();

        log.info("Follow user result: {}", result);
        return result;
    }

    @WriteDB(type = WriteDB.OperationType.DELETE)
    @Transactional
    @CacheEvict(value = "followStatus", key = "{#followerId, #followeeId}")
    public FollowStatusDTO unfollowUser(Long followerId, Long followeeId) {
        log.info("Unfollow user request: follower={}, followee={}", followerId, followeeId);

        // Validation
        validateUsers(followerId, followeeId);

        // Check if actually following
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.warn("User {} is not following user {}", followerId, followeeId);
            long followersCount = followRepository.countByFolloweeId(followeeId);
            return FollowStatusDTO.builder()
                    .isFollowing(false)
                    .following(false) // Add this field for compatibility
                    .followersCount(followersCount)
                    .message("Not following this user")
                    .build();
        }

        int deletedCount = followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        log.info("Deleted {} follow relationships", deletedCount);

        long followersCount = followRepository.countByFolloweeId(followeeId);

        FollowStatusDTO result = FollowStatusDTO.builder()
                .isFollowing(false)
                .following(false) // Add this field for compatibility
                .followersCount(followersCount)
                .message("Successfully unfollowed user")
                .build();

        log.info("Unfollow user result: {}", result);
        return result;
    }

    // ========== READ OPERATIONS ==========

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "followStatus", key = "{#followerId, #followeeId}")
    public FollowStatusDTO getFollowStatus(Long followerId, Long followeeId) {
        DatabaseContextHolder.setUserContext(followerId.toString());
        log.info("Get follow status: follower={}, followee={}", followerId, followeeId);

        if (followerId.equals(followeeId)) {
            return FollowStatusDTO.builder()
                    .isFollowing(false)
                    .following(false)
                    .followersCount(countFollowers(followeeId))
                    .message("Cannot follow yourself")
                    .build();
        }

        boolean isFollowing = followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
        long followersCount = countFollowers(followeeId);

        FollowStatusDTO result = FollowStatusDTO.builder()
                .isFollowing(isFollowing)
                .following(isFollowing) // Add this field for compatibility
                .followersCount(followersCount)
                .message(isFollowing ? "Following" : "Not following")
                .build();

        log.info("Follow status result: {}", result);
        return result;
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<UserDTO> getFollowers(Long userId, Pageable pageable) {
        DatabaseContextHolder.setUserContext(userId.toString());
        return followRepository.findByFolloweeId(userId, pageable)
                .map(this::convertFollowToUserDTO);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<UserDTO> getFollowing(Long userId, Pageable pageable) {
        DatabaseContextHolder.setUserContext(userId.toString());
        return followRepository.findByFollowerId(userId, pageable)
                .map(this::convertFollowingToUserDTO);
    }

    // ========== COUNT METHODS ==========

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "followerCount", key = "#userId")
    public long countFollowers(Long userId) {
        return followRepository.countByFolloweeId(userId);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "followingCount", key = "#userId")
    public long countFollowing(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    // ========== UTILITY METHODS ==========

    private void validateUsers(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new SelfFollowException();
        }

        if (!userRepository.existsById(followerId)) {
            throw new UserNotFoundException("Follower not found: " + followerId);
        }

        if (!userRepository.existsById(followeeId)) {
            throw new UserNotFoundException("Followee not found: " + followeeId);
        }
    }

    private FollowDTO convertToDTO(Follow follow) {
        return FollowDTO.builder()
                .id(follow.getId())
                .followerId(follow.getFollower().getId())
                .followerUsername(follow.getFollower().getUsername())
                .followerAvatar(follow.getFollower().getAvatar())
                .followerProfilePicture(follow.getFollower().getProfileImageUrl())
                .followeeId(follow.getFollowee().getId())
                .followeeUsername(follow.getFollowee().getUsername())
                .followeeAvatar(follow.getFollowee().getAvatar())
                .followeeProfilePicture(follow.getFollowee().getProfileImageUrl())
                .followedAt(follow.getFollowedAt() != null ? 
                    follow.getFollowedAt().toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime() : null)
                .build();
    }

    // Convert Follow to UserDTO for followers list (return follower info)
    private UserDTO convertFollowToUserDTO(Follow follow) {
        User follower = follow.getFollower();
        return UserDTO.builder()
                .id(follower.getId())
                .username(follower.getUsername())
                .email(follower.getEmail())
                .avatar(follower.getAvatar())
                .profileImageUrl(follower.getProfileImageUrl())
                .build();
    }

    // Convert Follow to UserDTO for following list (return followee info)
    private UserDTO convertFollowingToUserDTO(Follow follow) {
        User followee = follow.getFollowee();
        return UserDTO.builder()
                .id(followee.getId())
                .username(followee.getUsername())
                .email(followee.getEmail())
                .avatar(followee.getAvatar())
                .profileImageUrl(followee.getProfileImageUrl())
                .build();
    }
}