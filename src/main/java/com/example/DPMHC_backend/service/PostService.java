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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
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

    // CREATE POST
    @Transactional
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

    // GET POSTS
    @Transactional(readOnly = true)
    public Page<PostDTO> getAllPosts(Pageable pageable, String currentUserEmail) {
        return postRepository.findAll(pageable)
                .map(post -> mapToDTO(post, currentUserEmail));
    }

    @Transactional(readOnly = true)
    public Page<PostDTO> getPostsByUser(Long userId, Pageable pageable, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return postRepository.findByUser(user, pageable)
                .map(post -> mapToDTO(post, currentUserEmail));
    }

    @Transactional(readOnly = true)
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
    public List<PostDTO> getUserPosts(String userEmail, String currentUserEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return postRepository.findByUser(user).stream()
                .map(post -> mapToDTO(post, currentUserEmail))
                .toList();
    }

    @Transactional
    public void deletePost(Long postId, String currentUserEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        postRepository.delete(post);
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
                .likes(post.getLikesCount()) // Or use getLikesCount() if you have that field
                .isLikedByCurrentUser(isLiked)
                .commentsCount((int) commentsCount)
                .isBookmarkedByCurrentUser(isBookmarked)
                .createdAt(post.getCreatedAt())
                .username(post.getUser().getUsername())
                .userId(post.getUser().getId())
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class LikeResponse {
        private boolean isLiked;
        private int likesCount;
    }
}