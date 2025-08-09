package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.FollowDTO;
import com.example.DPMHC_backend.dto.FollowStatusDTO;
import com.example.DPMHC_backend.exception.FollowException;
import com.example.DPMHC_backend.exception.SelfFollowException;
import com.example.DPMHC_backend.exception.UserNotFoundException;
import com.example.DPMHC_backend.model.Follow;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.FollowRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    // ========== CORE FOLLOW OPERATIONS ==========
    public FollowStatusDTO toggleFollow(Long followerId, Long followeeId) {
        Optional<Follow> existingFollow = followRepository
                .findByFollowerIdAndFolloweeId(followerId, followeeId);

        if (existingFollow.isPresent()) {
            return unfollowUser(followerId, followeeId);  // Pass both IDs
        } else {
            return followUser(followerId, followeeId);
        }
    }

    public FollowStatusDTO followUser(Long followerId, Long followeeId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new RuntimeException("Follower not found"));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new RuntimeException("Followee not found"));

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowee(followee);
        followRepository.save(follow);

        long followersCount = followRepository.countByFolloweeId(followeeId);

        return FollowStatusDTO.builder()
                .isFollowing(true)
                .followersCount(followersCount)
                .message("Successfully followed user")
                .build();
    }

    // Change from private to public
    public FollowStatusDTO unfollowUser(Long followerId, Long followeeId) {
        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);

        long followersCount = followRepository.countByFolloweeId(followeeId);

        return FollowStatusDTO.builder()
                .isFollowing(false)
                .followersCount(followersCount)
                .message("Successfully unfollowed user")
                .build();
    }
    // ========== READ OPERATIONS ==========
    @Transactional(readOnly = true)
    @Cacheable(value = "followStatus", key = "{#followerId, #followeeId}")
    public FollowStatusDTO getFollowStatus(Long followerId, Long followeeId) {
        boolean isFollowing = followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
        return buildFollowStatusDTO(isFollowing, followeeId, null);
    }

    @Transactional(readOnly = true)
    public Page<FollowDTO> getFollowers(Long userId, Pageable pageable) {
        return followRepository.findByFolloweeId(userId, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<FollowDTO> getFollowing(Long userId, Pageable pageable) {
        return followRepository.findByFollowerId(userId, pageable)
                .map(this::convertToDTO);
    }


    // ========== UTILITY METHODS ==========
    private FollowStatusDTO buildFollowStatusDTO(boolean isFollowing, Long followeeId, String message) {
        return FollowStatusDTO.builder()
                .isFollowing(isFollowing)
                .followersCount(countFollowers(followeeId))
                .message(message)
                .build();
    }

    private FollowDTO convertToDTO(Follow follow) {
        return modelMapper.map(follow, FollowDTO.class);
    }

    private void validateUsers(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new SelfFollowException();
        }

        if (!userRepository.existsById(followerId) || !userRepository.existsById(followeeId)) {
            throw new UserNotFoundException("User not found");
        }
    }

    // Keep your existing count methods...
    public long countFollowers(Long userId) {
        return followRepository.countByFolloweeId(userId);
    }

    public long countFollowing(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}