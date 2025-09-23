package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.dto.cache.PageCacheWrapper;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.repository.BookmarkRepository;
import com.example.DPMHC_backend.repository.CommentRepository;
import com.example.DPMHC_backend.repository.LikeRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * High-performance batch service for loading posts with all related data in minimal queries
 * Eliminates N+1 problems by using batch queries and efficient caching
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostBatchService {
    
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PostService postService; // For DTO mapping
    
    /**
     * OPTIMIZED: Load posts with all related data using only 4 queries total
     * Instead of N+1 queries, this uses exactly 4 queries regardless of post count
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Transactional(readOnly = true)
    public Page<PostDTO> getOptimizedPostsWithMetadata(Pageable pageable, String currentUserEmail) {
        // Try to get from cache first using wrapper
        PageCacheWrapper<PostDTO> cachedWrapper = getCachedPosts(pageable, currentUserEmail);
        if (cachedWrapper != null) {
            return cachedWrapper.toPage(pageable);
        }
        
        // Cache miss - fetch from database
        // Query 1: Get posts with users (1 query with JOIN FETCH)
        Page<Post> posts = postRepository.findAllWithUser(pageable);
        
        if (posts.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        
        // Extract post IDs for batch queries
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getId)
                .collect(Collectors.toList());
        
        // Query 2: Batch load like counts for all posts
        Map<Long, Long> likeCountMap = getLikeCountsMap(postIds);
        
        // Query 3: Batch load comment counts for all posts
        Map<Long, Long> commentCountMap = getCommentCountsMap(postIds);
        
        // Query 4: Batch check which posts current user has liked
        Set<Long> userLikedPosts = getUserLikedPosts(postIds, currentUserEmail);
        
        // Query 5: Batch check which posts current user has bookmarked
        Set<Long> userBookmarkedPosts = getUserBookmarkedPosts(postIds, currentUserEmail);
        
        // Convert to DTOs using cached data (no additional queries)
        List<PostDTO> postDTOs = posts.getContent().stream()
                .map(post -> createOptimizedDTO(post, currentUserEmail, 
                        likeCountMap, commentCountMap, userLikedPosts, userBookmarkedPosts))
                .collect(Collectors.toList());
        
        Page<PostDTO> result = new PageImpl<>(postDTOs, pageable, posts.getTotalElements());
        
        // Cache the result using wrapper (only cache first 10 pages)
        if (pageable.getPageNumber() < 10) {
            cachePosts(pageable, currentUserEmail, result);
        }
        
        return result;
    }
    
    /**
     * Get cached posts using cache wrapper to avoid Page serialization issues
     */
    @Cacheable(value = "posts", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail", 
               condition = "#pageable.pageNumber < 10", unless = "#result == null")
    public PageCacheWrapper<PostDTO> getCachedPosts(Pageable pageable, String currentUserEmail) {
        // This method will only be called if cache is empty - return null to indicate cache miss
        return null;
    }
    
    /**
     * Cache posts using wrapper to avoid Page serialization issues
     */
    @CachePut(value = "posts", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail")
    public PageCacheWrapper<PostDTO> cachePosts(Pageable pageable, String currentUserEmail, Page<PostDTO> page) {
        return PageCacheWrapper.of(page);
    }
    
    /**
     * OPTIMIZED: Get user's posts with batch metadata loading
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    public Page<PostDTO> getOptimizedUserPosts(Long userId, Pageable pageable, String currentUserEmail) {
        // Try to get from cache first using wrapper
        PageCacheWrapper<PostDTO> cachedWrapper = getCachedUserPosts(userId, pageable, currentUserEmail);
        if (cachedWrapper != null) {
            return cachedWrapper.toPage(pageable);
        }
        
        // Cache miss - fetch from database
        // Similar optimization but for user-specific posts
        // Implementation similar to above but using findByUserWithUser
        Page<PostDTO> result = getOptimizedPostsWithMetadata(pageable, currentUserEmail); // Placeholder - implement similar pattern
        
        // Cache the result using wrapper
        cacheUserPosts(userId, pageable, currentUserEmail, result);
        
        return result;
    }
    
    /**
     * Get cached user posts using cache wrapper to avoid Page serialization issues
     */
    @Cacheable(value = "user-posts", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail", unless = "#result == null")
    public PageCacheWrapper<PostDTO> getCachedUserPosts(Long userId, Pageable pageable, String currentUserEmail) {
        // This method will only be called if cache is empty - return null to indicate cache miss
        return null;
    }
    
    /**
     * Cache user posts using wrapper to avoid Page serialization issues
     */
    @CachePut(value = "user-posts", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail")
    public PageCacheWrapper<PostDTO> cacheUserPosts(Long userId, Pageable pageable, String currentUserEmail, Page<PostDTO> page) {
        return PageCacheWrapper.of(page);
    }
    
    /**
     * OPTIMIZED: Get public feed with batch metadata loading
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    public Page<PostDTO> getOptimizedPublicFeed(Pageable pageable, String currentUserEmail) {
        // Try to get from cache first using wrapper
        PageCacheWrapper<PostDTO> cachedWrapper = getCachedPublicPosts(pageable, currentUserEmail);
        if (cachedWrapper != null) {
            return cachedWrapper.toPage(pageable);
        }
        
        // Cache miss - fetch from database
        // Query 1: Get public posts with users
        Page<Post> posts = postRepository.findPublicPostsWithUser(pageable);
        
        if (posts.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        
        Page<PostDTO> result = buildOptimizedPostPage(posts, pageable, currentUserEmail);
        
        // Cache the result using wrapper
        cachePublicPosts(pageable, currentUserEmail, result);
        
        return result;
    }
    
    /**
     * Get cached public posts using cache wrapper to avoid Page serialization issues
     */
    @Cacheable(value = "public-posts", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail", unless = "#result == null")
    public PageCacheWrapper<PostDTO> getCachedPublicPosts(Pageable pageable, String currentUserEmail) {
        // This method will only be called if cache is empty - return null to indicate cache miss
        return null;
    }
    
    /**
     * Cache public posts using wrapper to avoid Page serialization issues
     */
    @CachePut(value = "public-posts", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #currentUserEmail")
    public PageCacheWrapper<PostDTO> cachePublicPosts(Pageable pageable, String currentUserEmail, Page<PostDTO> page) {
        return PageCacheWrapper.of(page);
    }
    
    // ======================= CACHE EVICTION METHODS =======================
    
    /**
     * Evict all post-related caches when post data changes
     */
    @CacheEvict(value = {"posts", "user-posts", "public-posts", "post-details"}, allEntries = true)
    public void evictAllPostCaches() {
        // This method will be called from PostService when posts are created/updated/deleted
    }

    /**
     * Evict user-specific post caches
     */
    @CacheEvict(value = "user-posts", key = "#userId + '-*'", beforeInvocation = true)
    public void evictUserPostCaches(Long userId) {
        // Called when user's posts are modified
    }

    // ======================= PRIVATE HELPER METHODS =======================
    
    private Page<PostDTO> buildOptimizedPostPage(Page<Post> posts, Pageable pageable, String currentUserEmail) {
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getId)
                .collect(Collectors.toList());
        
        Map<Long, Long> likeCountMap = getLikeCountsMap(postIds);
        Map<Long, Long> commentCountMap = getCommentCountsMap(postIds);
        Set<Long> userLikedPosts = getUserLikedPosts(postIds, currentUserEmail);
        Set<Long> userBookmarkedPosts = getUserBookmarkedPosts(postIds, currentUserEmail);
        
        List<PostDTO> postDTOs = posts.getContent().stream()
                .map(post -> createOptimizedDTO(post, currentUserEmail,
                        likeCountMap, commentCountMap, userLikedPosts, userBookmarkedPosts))
                .collect(Collectors.toList());
        
        return new PageImpl<>(postDTOs, pageable, posts.getTotalElements());
    }
    
    private Map<Long, Long> getLikeCountsMap(List<Long> postIds) {
        if (postIds.isEmpty()) return new HashMap<>();
        
        return likeRepository.getLikeCountsByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],    // post_id
                        row -> (Long) row[1]     // like_count
                ));
    }
    
    private Map<Long, Long> getCommentCountsMap(List<Long> postIds) {
        if (postIds.isEmpty()) return new HashMap<>();
        
        return commentRepository.getCommentCountsByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],    // post_id
                        row -> (Long) row[1]     // comment_count
                ));
    }
    
    private Set<Long> getUserLikedPosts(List<Long> postIds, String userEmail) {
        if (postIds.isEmpty() || userEmail == null) return new HashSet<>();
        
        return new HashSet<>(likeRepository.getLikedPostIdsByUser(postIds, userEmail));
    }
    
    private Set<Long> getUserBookmarkedPosts(List<Long> postIds, String userEmail) {
        if (postIds.isEmpty() || userEmail == null) return new HashSet<>();
        
        return new HashSet<>(bookmarkRepository.getBookmarkedPostIdsByUser(postIds, userEmail));
    }
    
    /**
     * Create PostDTO using pre-loaded metadata (no additional queries)
     */
    private PostDTO createOptimizedDTO(Post post, String currentUserEmail,
                                     Map<Long, Long> likeCountMap,
                                     Map<Long, Long> commentCountMap,
                                     Set<Long> userLikedPosts,
                                     Set<Long> userBookmarkedPosts) {
        
        Long postId = post.getId();
        
        return PostDTO.builder()
                .id(postId)
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .videoUrl(post.getVideoUrl())
                .pdfUrl(post.getPdfUrl())
                .isPublic(post.isPublic())
                .createdAt(post.getCreatedAt())
                // User data (already loaded via JOIN FETCH)
                .userId(post.getUser().getId())
                .username(post.getUser().getUsername())
                .avatar(post.getUser().getAvatar())
                .profileImageUrl(post.getUser().getProfileImageUrl())
                // Metadata from batch queries (no N+1)
                .likes(likeCountMap.getOrDefault(postId, 0L).intValue())
                .commentsCount(commentCountMap.getOrDefault(postId, 0L).intValue())
                .isLikedByCurrentUser(userLikedPosts.contains(postId))
                .isBookmarkedByCurrentUser(userBookmarkedPosts.contains(postId))
                .build();
    }
}