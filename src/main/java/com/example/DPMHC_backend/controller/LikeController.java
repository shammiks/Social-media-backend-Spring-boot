package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{postId}")
    public ResponseEntity<?> toggleLike(@PathVariable Long postId,
                                        @AuthenticationPrincipal User user) {
        likeService.toggleLike(postId, user.getEmail());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getLikeCount(postId));
    }
}
