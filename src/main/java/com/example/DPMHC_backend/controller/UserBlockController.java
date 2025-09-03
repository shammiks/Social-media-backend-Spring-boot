package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.BlockUserRequestDTO;
import com.example.DPMHC_backend.dto.UserBlockDTO;
import com.example.DPMHC_backend.service.UserBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/blocks")
@RequiredArgsConstructor
@Slf4j
public class UserBlockController {

    private final UserBlockService userBlockService;

    /**
     * Block a user
     */
    @PostMapping
    public ResponseEntity<UserBlockDTO> blockUser(
            @Valid @RequestBody BlockUserRequestDTO request,
            Authentication authentication) {
        
        Long blockerId = getUserIdFromAuth(authentication);
        UserBlockDTO block = userBlockService.blockUser(blockerId, request.getUserId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(block);
    }

    /**
     * Unblock a user
     */
    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<Map<String, String>> unblockUser(
            @PathVariable Long blockedUserId,
            Authentication authentication) {
        
        Long blockerId = getUserIdFromAuth(authentication);
        userBlockService.unblockUser(blockerId, blockedUserId);
        
        return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
    }

    /**
     * Get all blocked users
     */
    @GetMapping
    public ResponseEntity<List<UserBlockDTO>> getBlockedUsers(Authentication authentication) {
        Long blockerId = getUserIdFromAuth(authentication);
        List<UserBlockDTO> blockedUsers = userBlockService.getBlockedUsers(blockerId);
        
        return ResponseEntity.ok(blockedUsers);
    }

    /**
     * Check if a specific user is blocked
     */
    @GetMapping("/check/{userId}")
    public ResponseEntity<Map<String, Boolean>> checkIfUserBlocked(
            @PathVariable Long userId,
            Authentication authentication) {
        
        Long blockerId = getUserIdFromAuth(authentication);
        boolean isBlocked = userBlockService.isSpecificUserBlocked(blockerId, userId);
        
        return ResponseEntity.ok(Map.of("isBlocked", isBlocked));
    }

    /**
     * Check if there's any block between two users (mutual check)
     */
    @GetMapping("/mutual-check/{userId}")
    public ResponseEntity<Map<String, Boolean>> checkMutualBlock(
            @PathVariable Long userId,
            Authentication authentication) {
        
        Long currentUserId = getUserIdFromAuth(authentication);
        boolean areBlocked = userBlockService.areUsersBlocked(currentUserId, userId);
        
        return ResponseEntity.ok(Map.of("areBlocked", areBlocked));
    }

    // Helper method to extract user ID from authentication
    private Long getUserIdFromAuth(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    // Error handling
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        log.error("UserBlock controller error: ", e);
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("Unexpected error in UserBlock controller: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}
