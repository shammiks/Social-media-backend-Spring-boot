package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RealTimeService realTimeService;
    private final NotificationService notificationService; // Add notification service

    // CREATE POST
    @Transactional
    @CacheEvict(value = {"user-posts", "postsByUser", "posts"}, allEntries = true)
    public Post createPost(String content, MultipartFile image, MultipartFile video,
                           MultipartFile pdf, boolean isPublic, String userEmail) {
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
            return "/" + uploadDir + "/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    // GET POSTS - OPTIMIZED VERSION
    @Transactional(readOnly = true)
    public Page<PostDTO> getAllPosts(Pageable pageable, String currentUserEmail) {
        Page<Post> postsPage = postRepository.findAllWithUser(pageable);
        List<PostDTO> optimizedDTOs = mapToDTOs(postsPage.getContent(), currentUserEmail);
        
        return new org.springframework.data.domain.PageImpl<>(
            optimizedDTOs, 
            pageable, 
            postsPage.getTotalElements()
        );
    }

    @Cacheable(value = "postById", key = "#postId")
    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "postsByUser", key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #currentUserEmail")
    public Page<PostDTO> getPostsByUser(Long userId, Pageable pageable, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<Post> postsPage = postRepository.findByUser(user, pageable);
        List<PostDTO> optimizedDTOs = mapToDTOs(postsPage.getContent(), currentUserEmail);
        
        return new org.springframework.data.domain.PageImpl<>(
            optimizedDTOs, 
            pageable, 
            postsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "postDTO", key = "#id + '_' + #currentUserEmail")
    public PostDTO getPost(Long id, String currentUserEmail) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.isPublic() && !post.getUser().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Not authorized to view this post");
        }

        return mapToDTO(post, currentUserEmail);
    }

    // UPDATE/DELETE POSTS
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

    @Transactional(readOnly = true)
    @Cacheable(value = "userLikeStatus", key = "#postId + '_' + #userEmail")
    public boolean hasUserLikedPost(Long postId, String userEmail) {
        if (userEmail == null) {
            return false; // Anonymous users haven't liked anything
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return likeRepository.existsByUserAndPost(user, post);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "user-posts", key = "#userEmail", unless = "#result == null")
    public List<PostDTO> getUserPosts(String userEmail, String currentUserEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> posts = postRepository.findByUser(user);
        return mapToDTOs(posts, currentUserEmail);
    }

    @Transactional
    @CacheEvict(value = {"postById", "postDTO", "postsByUser", "user-posts", "userLikeStatus", "posts"}, allEntries = true)
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
    // LIKES
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

    // DTO MAPPING - OPTIMIZED VERSION
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

    // OPTIMIZED BATCH DTO MAPPING - ELIMINATES N+1 PROBLEMS
    public List<PostDTO> mapToDTOs(List<Post> posts, String currentUserEmail) {
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }

        // Batch fetch liked post IDs for current user
        final Set<Long> likedPostIds = currentUserEmail != null 
            ? new HashSet<>(likeRepository.findLikedPostIdsByUserEmail(posts, currentUserEmail))
            : Collections.emptySet();

        // Batch fetch bookmarked post IDs for current user
        final Set<Long> bookmarkedPostIds = currentUserEmail != null 
            ? new HashSet<>(bookmarkRepository.findBookmarkedPostIdsByUserEmail(posts, currentUserEmail))
            : Collections.emptySet();

        // Batch fetch comment counts
        Map<Long, Long> commentCounts = commentRepository.countCommentsByPosts(posts).stream()
                .collect(Collectors.toMap(
                    result -> (Long) result[0],
                    result -> (Long) result[1]
                ));

        // Map to DTOs using the batched data
        return posts.stream()
                .map(post -> PostDTO.builder()
                    .id(post.getId())
                    .content(post.getContent())
                    .imageUrl(post.getImageUrl())
                    .videoUrl(post.getVideoUrl())
                    .pdfUrl(post.getPdfUrl())
                    .isPublic(post.isPublic())
                    .likes(post.getLikesCount())
                    .isLikedByCurrentUser(likedPostIds.contains(post.getId()))
                    .commentsCount(Math.toIntExact(commentCounts.getOrDefault(post.getId(), 0L)))
                    .isBookmarkedByCurrentUser(bookmarkedPostIds.contains(post.getId()))
                    .createdAt(post.getCreatedAt())
                    .username(post.getUser().getUsername())
                    .userId(post.getUser().getId())
                    .avatar(post.getUser().getAvatar())
                    .profileImageUrl(post.getUser().getProfileImageUrl())
                    .build())
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    public static class LikeResponse {
        private boolean isLiked;
        private int likesCount;
    }
}