package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.FollowDTO;
import com.example.DPMHC_backend.dto.FollowStatusDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import com.example.DPMHC_backend.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // ========== MAIN ENDPOINTS ==========
    @PostMapping("/toggle")
    public ResponseEntity<FollowStatusDTO> toggleFollow(
            @RequestParam Long followeeId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Get the User object directly from authentication principal
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                followService.toggleFollow(currentUser.getId(), followeeId)
        );
    }

    @GetMapping("/status")
    public ResponseEntity<FollowStatusDTO> getFollowStatus(
            @RequestParam Long followeeId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Get the User object directly from authentication principal
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                followService.getFollowStatus(currentUser.getId(), followeeId)
        );
    }

    // ========== LIST ENDPOINTS ==========
    @GetMapping("/followers/{userId}")
    public ResponseEntity<Page<FollowDTO>> getFollowers(
            @PathVariable Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(
                followService.getFollowers(userId, pageable)
        );
    }

    @GetMapping("/following/{userId}")
    public ResponseEntity<Page<FollowDTO>> getFollowing(
            @PathVariable Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(
                followService.getFollowing(userId, pageable)
        );
    }

    // ========== COUNT ENDPOINTS ==========
    @GetMapping("/count/followers/{userId}")
    public ResponseEntity<Long> countFollowers(@PathVariable Long userId) {
        return ResponseEntity.ok(
                followService.countFollowers(userId)
        );
    }

    @GetMapping("/count/following/{userId}")
    public ResponseEntity<Long> countFollowing(@PathVariable Long userId) {
        return ResponseEntity.ok(
                followService.countFollowing(userId)
        );
    }

    // ========== FOLLOW/UNFOLLOW ENDPOINTS ==========
    @PostMapping("/follow/{userId}")
    public ResponseEntity<FollowStatusDTO> followUser(
            @PathVariable Long userId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Get the User object directly from authentication principal
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                followService.followUser(currentUser.getId(), userId)
        );
    }

    @PostMapping("/unfollow/{userId}")
    public ResponseEntity<FollowStatusDTO> unfollowUser(
            @PathVariable Long userId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Get the User object directly from authentication principal
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                followService.unfollowUser(currentUser.getId(), userId)
        );
    }

    @GetMapping("/status/{followeeId}")
    public ResponseEntity<FollowStatusDTO> getFollowStatusPath(
            @PathVariable Long followeeId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Get the User object directly from authentication principal
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                followService.getFollowStatus(currentUser.getId(), followeeId)
        );
    }
}