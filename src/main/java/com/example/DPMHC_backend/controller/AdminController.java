package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.UserWarning;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import com.example.DPMHC_backend.repository.UserWarningRepository;
import com.example.DPMHC_backend.repository.CommentRepository;
import com.example.DPMHC_backend.repository.LikeRepository;
import com.example.DPMHC_backend.repository.BookmarkRepository;
import com.example.DPMHC_backend.service.EmailService;
import com.example.DPMHC_backend.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserWarningRepository userWarningRepository;
    private final EmailService emailService;
    private final PostService postService;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;

    /**
     * Get all posts for admin moderation (paginated)
     */
    @GetMapping("/posts")
    public ResponseEntity<Page<AdminPostDTO>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<Post> posts = postRepository.findAll(pageable);
        Page<AdminPostDTO> postDTOs = posts.map(this::mapToAdminPostDTO);
        
        return ResponseEntity.ok(postDTOs);
    }

    /**
     * Get only reported posts
     */
    @GetMapping("/reported-posts")
    public ResponseEntity<List<AdminPostDTO>> getReportedPosts() {
        List<AdminPostDTO> reportedPosts = postRepository.findAll().stream()
                .filter(Post::isReported)
                .map(this::mapToAdminPostDTO)
                .toList();
        return ResponseEntity.ok(reportedPosts);
    }

    /**
     * Issue a warning to a user for a specific post
     */
    @PostMapping("/warn-user")
    public ResponseEntity<?> warnUser(@RequestBody WarningRequest request) {
        try {
            // Get admin user info from authentication
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User adminUser = null;
            
            // The principal should be the User object set by JWT filter
            if (auth.getPrincipal() instanceof User) {
                adminUser = (User) auth.getPrincipal();
            } else if (auth.getName() != null) {
                // Fallback: get user by email if principal is not User object
                adminUser = userRepository.findByEmail(auth.getName())
                        .orElseThrow(() -> new RuntimeException("Admin user not found"));
            }
            
            if (adminUser == null) {
                return ResponseEntity.status(401).body("Admin authentication failed");
            }
            
            // Verify admin role
            if (!adminUser.isAdmin()) {
                return ResponseEntity.status(403).body("Access denied: Admin role required");
            }

            // Get the user to be warned
            User targetUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if user is already banned
            if (targetUser.isBanned()) {
                return ResponseEntity.badRequest().body("User is already banned");
            }

            // Count existing warnings for this user
            long warningCount = userWarningRepository.countByUserId(request.getUserId());
            boolean isFinalWarning = warningCount >= 0; // First warning is also considered "final" since next violation = ban

            // Get post if provided
            Post post = null;
            if (request.getPostId() != null) {
                post = postRepository.findById(request.getPostId()).orElse(null);
            }

            // Create warning record
            UserWarning warning = UserWarning.builder()
                    .user(targetUser)
                    .post(post)
                    .reason(request.getReason())
                    .warningMessage(request.getWarningMessage())
                    .issuedBy(adminUser.getId())
                    .emailSent(false)
                    .build();

            warning = userWarningRepository.save(warning);

            // Send warning email
            try {
                emailService.sendWarningEmail(
                        targetUser.getEmail(),
                        targetUser.getUsername(),
                        request.getReason(),
                        request.getWarningMessage(),
                        isFinalWarning
                );
                warning.setEmailSent(true);
                userWarningRepository.save(warning);
            } catch (Exception emailException) {
                // Log email failure but don't fail the warning
                System.err.println("Failed to send warning email: " + emailException.getMessage());
            }

            return ResponseEntity.ok().body("Warning issued successfully. User now has " + (warningCount + 1) + " warning(s).");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error issuing warning: " + e.getMessage());
        }
    }

    /**
     * Ban a user (only if they already have a warning)
     */
    @PostMapping("/ban-user")
    public ResponseEntity<?> banUser(@RequestBody BanRequest request) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if user already banned
            if (user.isBanned()) {
                return ResponseEntity.badRequest().body("User is already banned");
            }

            // Check if user has any warnings
            long warningCount = userWarningRepository.countByUserId(request.getUserId());
            if (warningCount == 0) {
                return ResponseEntity.badRequest().body("Cannot ban user without prior warning. Please issue a warning first.");
            }

            // Ban the user
            user.setBanned(true);
            userRepository.save(user);

            // Send ban notification email
            try {
                emailService.sendBanNotificationEmail(
                        user.getEmail(),
                        user.getUsername(),
                        request.getReason()
                );
            } catch (Exception emailException) {
                // Log email failure but don't fail the ban
                System.err.println("Failed to send ban notification email: " + emailException.getMessage());
            }

            return ResponseEntity.ok().body("User banned successfully after " + warningCount + " warning(s).");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error banning user: " + e.getMessage());
        }
    }

    /**
     * Delete a post - Admin only, bypasses all user-level restrictions
     */
    @DeleteMapping("/posts/{postId}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deletePost(@PathVariable Long postId) {
        try {
            System.out.println("=== DELETE POST DEBUG ===");
            System.out.println("Admin attempting to delete post ID: " + postId);
            
            // Get current authentication context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                System.out.println("Authentication principal: " + auth.getPrincipal().getClass().getSimpleName());
                System.out.println("Authentication authorities: " + auth.getAuthorities());
                System.out.println("Is authenticated: " + auth.isAuthenticated());
            } else {
                System.out.println("No authentication context found!");
            }
            
            // Check if post exists first
            if (!postRepository.existsById(postId)) {
                System.out.println("Post not found with ID: " + postId);
                return ResponseEntity.notFound().build();
            }
            
            // Get post details for debugging
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent()) {
                Post post = postOpt.get();
                System.out.println("Post owner: " + post.getUser().getEmail());
                System.out.println("Post owner banned status: " + post.getUser().isBanned());
                System.out.println("Post owner admin status: " + post.getUser().isAdmin());
            }
            
            // Admin can delete any post, so bypass the authorization in PostService
            // Delete related entities first to avoid foreign key constraint issues
            System.out.println("Deleting comments...");
            commentRepository.deleteByPostId(postId);
            commentRepository.flush();
            
            System.out.println("Deleting likes...");
            likeRepository.deleteByPostId(postId);
            likeRepository.flush();
            
            System.out.println("Deleting bookmarks...");
            bookmarkRepository.deleteByPostId(postId);
            bookmarkRepository.flush();
            
            System.out.println("Deleting user warnings...");
            userWarningRepository.deleteByPostId(postId);
            userWarningRepository.flush();
            
            // Finally delete the post itself
            System.out.println("Deleting post...");
            postRepository.deleteById(postId);
            postRepository.flush();
            
            System.out.println("Post deleted successfully!");
            return ResponseEntity.ok("Post deleted successfully.");
        } catch (Exception e) {
            System.err.println("=== DELETE POST ERROR ===");
            System.err.println("Error type: " + e.getClass().getSimpleName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace(); // Log the full stack trace for debugging
            return ResponseEntity.badRequest().body("Error deleting post: " + e.getMessage());
        }
    }

    /**
     * Get user warning history
     */
    @GetMapping("/users/{userId}/warnings")
    public ResponseEntity<List<UserWarning>> getUserWarnings(@PathVariable Long userId) {
        List<UserWarning> warnings = userWarningRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(warnings);
    }

    /**
     * Map Post entity to AdminPostDTO with warning information
     */
    private AdminPostDTO mapToAdminPostDTO(Post post) {
        // Get warning count for the post's user
        long warningCount = userWarningRepository.countByUserId(post.getUser().getId());
        List<UserWarning> userWarnings = userWarningRepository.findByUserIdOrderByCreatedAtDesc(post.getUser().getId());
        
        UserWarning lastWarning = userWarnings.isEmpty() ? null : userWarnings.get(0);

        return AdminPostDTO.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .videoUrl(post.getVideoUrl())
                .pdfUrl(post.getPdfUrl())
                .isPublic(post.isPublic())
                .reported(post.isReported())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getComments() != null ? post.getComments().size() : 0)
                .createdAt(post.getCreatedAt())
                // User information
                .userId(post.getUser().getId())
                .username(post.getUser().getUsername())
                .userEmail(post.getUser().getEmail())
                .userAvatar(post.getUser().getAvatar())
                .userBanned(post.getUser().isBanned())
                .userIsAdmin(post.getUser().isAdmin())
                // Warning information
                .warningCount((int) warningCount)
                .hasWarnings(warningCount > 0)
                .lastWarningDate(lastWarning != null ? lastWarning.getCreatedAt() : null)
                .lastWarningReason(lastWarning != null ? lastWarning.getReason() : null)
                .build();
    }


}

