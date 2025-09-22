package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RealTimeService realTimeService;
    private final NotificationService notificationService; // Add notification service

    /**
     * CREATE POST with cache eviction for user's posts
     */
    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @CacheEvict(value = "postsByUser", key = "#result.user.id + '_0_*'")
    public Post createPost(String content, MultipartFile image, MultipartFile video,
                           MultipartFile pdf, boolean isPublic, String userEmail) {
        // Set user context for routing
        DatabaseContextHolder.setUserContext(userEmail);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String imageUrl = storeFile(image);
        String videoUrl = storeFile(video);
        String pdfUrl = storeFile(pdf);

        Post post = Post.builder()
                .content(content)
                .imageUrl(imageUrl)
                .videoUrl(videoUrl)
                .pdfUrl(pdfUrl)
                .isPublic(isPublic)
                .user(user)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        Post savedPost = postRepository.save(post);
        realTimeService.broadcastNewPost(savedPost);
        return savedPost;
    }

    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
            String uploadDir = "uploads";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
            return "/uploads/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    // OPTIMIZED: GET POSTS - Public feed with single query (eliminates N+1 for users)
    // Note: Not caching paginated results as they change frequently and keys would be complex
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Transactional(readOnly = true)
    public Page<PostDTO> getAllPosts(Pageable pageable, String currentUserEmail) {
        return postRepository.findAllWithUser(pageable)
                .map(post -> mapToDTO(post, currentUserEmail));
    }

    /**
     * CACHED: Get post by ID with Redis caching (10min TTL)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Transactional(readOnly = true)
    @Cacheable(value = "posts", key = "#postId", unless = "#result == null")
    public Post getPostById(Long postId) {
        log.debug("🔍 Cache MISS: Loading Post entity for ID: {}", postId);
        return postRepository.findByIdWithUser(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));
    }

    /**
     * CACHED: Get posts by user - cache first page only for performance
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "postsByUser", key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize", 
               condition = "#pageable.pageNumber == 0", unless = "#result == null || #result.empty")
    public Page<PostDTO> getPostsByUser(Long userId, Pageable pageable, String currentUserEmail) {
        log.debug("🔍 Cache MISS: Loading posts for user ID: {}, page: {}", userId, pageable.getPageNumber());
        // Set user context for consistent routing
        DatabaseContextHolder.setUserContext(currentUserEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // OPTIMIZED: Use optimized query with user data pre-fetched
        return postRepository.findByUserWithUser(user, pageable)
                .map(post -> mapToDTO(post, currentUserEmail));
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true, fallbackToMaster = true)
    @Transactional(readOnly = true)
    public PostDTO getPost(Long id, String currentUserEmail) {
        // Set user context for consistent routing
        DatabaseContextHolder.setUserContext(currentUserEmail);
        // OPTIMIZED: Use optimized query with user data pre-fetched
        Post post = postRepository.findByIdWithUser(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.isPublic() && !post.getUser().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Not authorized to view this post");
        }

        return mapToDTO(post, currentUserEmail);
    }

    // UPDATE/DELETE POSTS
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    public void updatePost(Long postId, Post updatedPost, String currentUserEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Not authorized to update this post");
        }

        post.setContent(updatedPost.getContent());
        post.setImageUrl(updatedPost.getImageUrl());
        post.setVideoUrl(updatedPost.getVideoUrl());
        post.setPdfUrl(updatedPost.getPdfUrl());
        post.setIsPublic(updatedPost.isPublic());
        post.setUpdatedAt(new Date());

        postRepository.save(post);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public boolean hasUserLikedPost(Long postId, String userEmail) {
        DatabaseContextHolder.setUserContext(userEmail);
        if (userEmail == null) {
            return false; // Anonymous users haven't liked anything
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return likeRepository.existsByUserAndPost(user, post);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public List<PostDTO> getUserPosts(String userEmail, String currentUserEmail) {
        DatabaseContextHolder.setUserContext(userEmail);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return postRepository.findByUser(user).stream()
                .map(post -> mapToDTO(post, currentUserEmail))
                .toList();
    }

    @WriteDB(type = WriteDB.OperationType.DELETE)
    @Transactional
    public void deletePost(Long postId, String currentUserEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        commentRepository.deleteByPostId(postId);

        likeRepository.deleteByPostId(postId);

        bookmarkRepository.deleteByPostId(postId);

        postRepository.deleteById(postId);
    }
    // LIKES - Social media interactions, write to master
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    public LikeResponse toggleLike(Long postId, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Like> existingLike = likeRepository.findByPostAndUser(post, user);

        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
            postRepository.save(post);
            return new LikeResponse(false, post.getLikesCount());
        } else {
            Like like = Like.builder()
                    .post(post)
                    .user(user)
                    .build();

            likeRepository.save(like);
            post.setLikesCount(post.getLikesCount() + 1);
            postRepository.save(post);

            // Trigger notification for post like
            notificationService.handlePostLike(post.getUser().getEmail(), userEmail, postId);

            return new LikeResponse(true, post.getLikesCount());
        }
    }

    // DTO MAPPING
    public PostDTO mapToDTO(Post post, String currentUserEmail) {
        boolean isLiked = currentUserEmail != null &&
                likeRepository.existsByPostAndUserEmail(post, currentUserEmail);

        boolean isBookmarked = currentUserEmail != null &&
                bookmarkRepository.existsByPostAndUserEmail(post, currentUserEmail);

        long commentsCount = commentRepository.countByPost(post);

    return PostDTO.builder()
        .id(post.getId())
        .content(post.getContent())
        .imageUrl(post.getImageUrl())
        .videoUrl(post.getVideoUrl())
        .pdfUrl(post.getPdfUrl())
        .isPublic(post.isPublic())
        .likes(post.getLikesCount())
        .isLikedByCurrentUser(isLiked)
        .commentsCount((int) commentsCount)
        .isBookmarkedByCurrentUser(isBookmarked)
        .createdAt(post.getCreatedAt())
        .username(post.getUser().getUsername())
        .userId(post.getUser().getId())
        .avatar(post.getUser().getAvatar())
        .profileImageUrl(post.getUser().getProfileImageUrl())
        .build();
    }



    @Data
    @AllArgsConstructor
    public static class LikeResponse {
        private boolean isLiked;
        private int likesCount;
    }
}