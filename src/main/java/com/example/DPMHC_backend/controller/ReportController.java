package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/report")
@RequiredArgsConstructor
public class ReportController {

    private final PostRepository postRepository;

    @PostMapping("/{postId}")
    public ResponseEntity<String> reportPost(@PathVariable Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setReported(true);
        postRepository.save(post);

        return ResponseEntity.ok("Post reported successfully.");
    }
}

