package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @GetMapping("/reported-posts")
    public ResponseEntity<List<PostDTO>> getReportedPosts() {
        List<PostDTO> reportedPosts = postRepository.findAll().stream()
                .filter(Post::isReported)
                .map(this::mapToDTO)
                .toList();
        return ResponseEntity.ok(reportedPosts);
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId) {
        postRepository.deleteById(postId);
        return ResponseEntity.ok("Post deleted successfully.");
    }

    @PostMapping("/ban-user/{userId}")
    public ResponseEntity<String> banUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setBanned(true); // Add a banned flag in User entity
        userRepository.save(user);
        return ResponseEntity.ok("User banned successfully.");
    }

    private PostDTO mapToDTO(Post post) {
        return PostDTO.builder()
                .id(post.getId())
                .content(post.getContent())
                .isPublic(post.isPublic())
                .createdAt(post.getCreatedAt())
                .build();
    }
}

