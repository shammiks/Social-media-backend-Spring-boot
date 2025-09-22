package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.model.Like;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.LikeRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Caching(evict = {
        @CacheEvict(value = "post-likes", key = "#postId"),
        @CacheEvict(value = "user-likes", key = "#email + ':' + #postId"),
        @CacheEvict(value = "posts", key = "#postId") // Evict post cache as like count changed
    })
    public void toggleLike(Long postId, String email) {
        log.debug("🔄 Toggling like for post {} by user {}", postId, email);
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (likeRepository.existsByUserAndPost(user, post)) {
            likeRepository.deleteByUserAndPost(user, post); // unlike
            log.debug("👎 User {} unliked post {}", email, postId);
        } else {
            likeRepository.save(Like.builder().user(user).post(post).build()); // like
            log.debug("👍 User {} liked post {}", email, postId);
        }
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Cacheable(value = "post-likes", key = "#postId", unless = "#result == null")
    public long getLikeCount(Long postId) {
        log.debug("📊 Getting like count for post {}", postId);
        
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        long likeCount = likeRepository.countByPost(post);
        log.debug("📈 Post {} has {} likes", postId, likeCount);
        return likeCount;
    }

    /**
     * Check if a specific user liked a specific post
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Cacheable(value = "user-likes", key = "#email + ':' + #postId")
    public boolean hasUserLikedPost(Long postId, String email) {
        log.debug("🔍 Checking if user {} liked post {}", email, postId);
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        boolean hasLiked = likeRepository.existsByUserAndPost(user, post);
        log.debug("💖 User {} {} post {}", email, hasLiked ? "liked" : "hasn't liked", postId);
        return hasLiked;
    }

    /**
     * Get posts liked by a specific user (for user profile)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Cacheable(value = "user-liked-posts", key = "#userId + ':page:' + #page + ':size:' + #size")
    public List<Long> getUserLikedPostIds(Long userId, int page, int size) {
        log.debug("📋 Getting liked posts for user {} (page: {}, size: {})", userId, page, size);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get liked post IDs with efficient query and apply pagination
        List<Long> allLikedPostIds = likeRepository.findPostIdsByUserId(userId);
        List<Long> likedPostIds = allLikedPostIds.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
        
        log.debug("✅ Found {} liked posts for user {}", likedPostIds.size(), userId);
        return likedPostIds;
    }
}
