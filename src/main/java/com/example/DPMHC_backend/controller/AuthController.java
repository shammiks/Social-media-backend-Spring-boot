//package com.example.DPMHC_backend.controller;

package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import com.example.DPMHC_backend.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.example.DPMHC_backend.dto.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            String token = userService.register(user);
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (RuntimeException e) {
            // return JSON with message field
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            String token = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
            UserDTO userDTO = userService.getUserByEmail(loginRequest.getEmail()); // Already returns DTO
            return ResponseEntity.ok(new AuthResponseWithUser(token, userDTO));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }




    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        try {
            userService.verifyEmail(token);
            return ResponseEntity.ok("<h2>Email verified successfully. You can now log in to the app.</h2>");
        } catch (RuntimeException e) {
            return ResponseEntity
                    .badRequest()
                    .body("<h2>Verification failed: " + e.getMessage() + "</h2>");
        }
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<?> requestPasswordReset(@RequestBody EmailRequest emailRequest) {
        return ResponseEntity.ok(userService.sendPasswordResetLink(emailRequest.getEmail()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Password has been reset successfully.");
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<UserDTO> updateAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        try {
            // Debug logging
            System.out.println("=== DEBUG AVATAR UPLOAD ===");
            System.out.println("Authentication object: " + authentication);
            System.out.println("Is authenticated: " + (authentication != null ? authentication.isAuthenticated() : "null"));
            System.out.println("Principal: " + (authentication != null ? authentication.getPrincipal() : "null"));
            System.out.println("Authorities: " + (authentication != null ? authentication.getAuthorities() : "null"));

            if (authentication == null) {
                System.out.println("Authentication is null!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!authentication.isAuthenticated()) {
                System.out.println("User is not authenticated!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get the User object from authentication
            User currentUser = (User) authentication.getPrincipal();
            System.out.println("Current user email: " + currentUser.getEmail());
            System.out.println("Current user role: " + currentUser.getRole());

            UserDTO updatedUser = userService.updateAvatar(file, currentUser.getEmail());
            return ResponseEntity.ok(updatedUser);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException: Principal is not a User object: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            System.out.println("General Exception: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/me/bio")
    public ResponseEntity<UserDTO> updateBio(@RequestBody BioUpdateRequest request, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User currentUser = (User) authentication.getPrincipal();
            UserDTO updatedUser = userService.updateBio(request.getBio(), currentUser.getEmail());
            return ResponseEntity.ok(updatedUser);
        } catch (ClassCastException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(UserDTO.builder().build()); // Return empty DTO with error
        }
    }

    @GetMapping("/users/by-username/{username}")
    public ResponseEntity<UserDTO> getUserByUsername(@PathVariable String username) {
        User user = (User) userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar()) // adjust if your field name is different
                .bio(user.getBio())       // adjust if your field name is different
                .isAdmin(user.isAdmin())  // adjust if your field name is different
                .build();

        return ResponseEntity.ok(userDTO);
    }




    @Data
    static class ResetPasswordRequest {
        private String token;
        private String newPassword;
    }

    @Data
    static class EmailRequest {
        private String email;
    }

    @Data
    static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    static class AuthResponse {
        private final String token;
    }
    @Data
    static class ErrorResponse {
        private final String message;
    }
    @Data
    @AllArgsConstructor
    static class AuthResponseWithUser {
        private String token;
        private UserDTO user;
    }
    @Data
    static class BioUpdateRequest {
        private String bio;
    }


}
