package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.LoginResponse;
import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.PasswordResetToken;
import com.example.DPMHC_backend.model.RefreshToken;
import com.example.DPMHC_backend.model.Role;
import com.example.DPMHC_backend.dto.UserDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.PasswordResetTokenRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import com.example.DPMHC_backend.repository.UserBlockRepository;
import com.example.DPMHC_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.DPMHC_backend.model.VerificationToken;
import com.example.DPMHC_backend.repository.VerificationTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final CloudinaryService cloudinaryService;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PostRepository postRepository;
    private final PostService postService; // Add this dependency
    private final UserBlockRepository userBlockRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.base.url}")
    private String baseUrl;


    private final List<String> allowedDomains = List.of("gmail.com", "yahoo.com", "outlook.com");
    private void validateEmailDomain(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (!allowedDomains.contains(domain)) {
            throw new RuntimeException("Only Gmail, Yahoo, and Outlook email addresses are allowed.");
        }
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    public String register(User user) {
        validateEmailDomain(user.getEmail());
        // Check if username is null and handle it
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username cannot be null or empty");
        }
        String normalizedUsername = user.getUsername().trim();
        user.setUsername(normalizedUsername);

        log.info("Checking if email exists: {}", user.getEmail());
        boolean emailExists = userRepository.existsByEmailIgnoreCase(user.getEmail());
        log.info("Email exists: {}", emailExists);

        if (emailExists) {
            throw new RuntimeException("Email already registered");
        }

//        if (userRepository.existsByEmail(user.getEmail())) {
//            throw new RuntimeException("Email already registered");
//        }

        // Check for case-insensitive username existence
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new RuntimeException("Username already taken");
        };

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(Role.USER);
        user.setBanned(false);
    user.setAdmin(false); // Ensure isAdmin is always set for new users
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        user.setEmailVerified(false);


        userRepository.save(user);

        // Send verification email...
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .build();
        tokenRepository.save(verificationToken);

        String link = baseUrl + "/api/auth/verify?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), link);

        return "Verification email sent. Please check your inbox.";
    }


    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    public String verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Token expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        tokenRepository.delete(verificationToken); // Optional: delete after verification
        return "Email verified successfully.";
    }


    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true, fallbackToMaster = true)
    @Transactional(readOnly = true)
    public String login(String email, String password) {
        // Set user context for routing
        DatabaseContextHolder.setUserContext(email);
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = optionalUser.get();

        if (user.isBanned()) {
            throw new RuntimeException("This user is banned.");
        }

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email is not verified");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtService.generateToken(user);
    }

    /**
     * Login with refresh token support
     */
    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    public LoginResponse loginWithRefreshToken(String email, String password) {
        // Set user context for routing
        DatabaseContextHolder.setUserContext(email);
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = optionalUser.get();

        if (user.isBanned()) {
            throw new RuntimeException("This user is banned.");
        }

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email is not verified");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Generate access token
        String accessToken = jwtService.generateToken(user);
        
        // Create refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        
        // JWT expiration time in seconds (5 minutes)
        Long expiresIn = 300L;
        
        return new LoginResponse(accessToken, refreshToken.getToken(), expiresIn);
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    public String sendPasswordResetCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No user found with this email"));

        // Find and delete any existing reset tokens for this user
        List<PasswordResetToken> existingTokens = resetTokenRepository.findByUser(user);
        if (!existingTokens.isEmpty()) {
            resetTokenRepository.deleteAll(existingTokens);
            resetTokenRepository.flush(); // Force the delete to be executed immediately
        }
        
        // Generate a 5-digit code
        String resetCode = String.format("%05d", (int)(Math.random() * 100000));
        
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(resetCode) // Store the 5-digit code in the token field
                .user(user)
                .expiryDate(new Date(System.currentTimeMillis() + 1000 * 60 * 15)) // 15 minutes expiry
                .verified(false) // Explicitly set verified to false
                .build();

        resetTokenRepository.save(resetToken);

        // Send email with the 5-digit code
        emailService.sendPasswordResetCodeEmail(user.getEmail(), resetCode);

        return "Reset code sent to your email";
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true, fallbackToMaster = true)
    @Transactional(readOnly = true)
    public boolean verifyResetCode(String email, String code) {
        DatabaseContextHolder.setUserContext(email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("No user found with this email"));

            PasswordResetToken resetToken = resetTokenRepository.findByUserAndToken(user, code)
                    .orElse(null);

            if (resetToken == null) {
                return false;
            }

            if (resetToken.getExpiryDate().before(new Date())) {
                resetTokenRepository.delete(resetToken); // Clean up expired token
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @WriteDB(type = WriteDB.OperationType.UPDATE, critical = true)
    @Transactional
    public String resetPasswordWithCode(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No user found with this email"));

        PasswordResetToken resetToken = resetTokenRepository.findByUserAndToken(user, code)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset code"));

        if (resetToken.getExpiryDate().before(new Date())) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset code has expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(new Date());

        userRepository.save(user);
        resetTokenRepository.delete(resetToken); // Remove token after use

        return "Password reset successful.";
    }



    /**
     * CACHED: Get user DTO by email with Redis caching (15min TTL)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "userProfiles", key = "#email", unless = "#result == null")
    public UserDTO getUserByEmail(String email) {
        log.debug("🔍 Cache MISS: Loading UserDTO for email: {}", email);
        DatabaseContextHolder.setUserContext(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToDTO(user);
    }

    /**
     * CACHED: Get user entity by email with Redis caching (15min TTL)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    @Cacheable(value = "usersByEmail", key = "#email", unless = "#result == null")
    public User getUserEntityByEmail(String email) {
        log.debug("🔍 Cache MISS: Loading User entity for email: {}", email);
        DatabaseContextHolder.setUserContext(email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * UPDATE: Avatar with cache eviction
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "usersByEmail", key = "#email"),
        @CacheEvict(value = "userProfiles", key = "#email"),
        @CacheEvict(value = "usersById", key = "#result.id")
    })
    public UserDTO updateAvatar(MultipartFile file, String email) throws IOException {
        log.debug("🗑️ Cache EVICT: Clearing user caches for email: {}", email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String imageUrl = cloudinaryService.uploadFile(file, "avatars");
        user.setAvatar(imageUrl);
        userRepository.save(user);
        return mapToDTO(user);
    }

    /**
     * UPDATE: Bio with cache eviction
     */
    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "usersByEmail", key = "#email"),
        @CacheEvict(value = "userProfiles", key = "#email"),
        @CacheEvict(value = "usersById", key = "#result.id")
    })
    public UserDTO updateBio(String bio, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Validate bio length (optional - you can adjust the limit)
        if (bio != null && bio.length() > 500) {
            throw new RuntimeException("Bio cannot exceed 500 characters");
        }

        user.setBio(bio);
        user.setUpdatedAt(new Date());
        userRepository.save(user);
        return mapToDTO(user);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @Transactional(readOnly = true)
    public Page<PostDTO> getPostsByUser(Long userId, Pageable pageable, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return postRepository.findByUser(user, pageable)
                .map(post -> postService.mapToDTO(post, currentUserEmail));
    }



    public UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .isAdmin(user.isAdmin())
                .build();
    }

    /**
     * Create UserDTO with blocking context - hides profile picture if users are blocked
     */
    public UserDTO createUserDTOWithBlockingContext(User user, Long currentUserId) {
        if (user == null) {
            return null;
        }

        UserDTO userDTO = mapToDTO(user);

        // If there's a current user and they're blocked from each other, hide profile info
        if (currentUserId != null && !currentUserId.equals(user.getId())) {
            boolean areBlocked = userBlockRepository.areUsersBlocked(currentUserId, user.getId());
            if (areBlocked) {
                // Hide profile picture and other sensitive info for blocked users
                userDTO.setProfileImageUrl(null);
                userDTO.setAvatar(null);
                userDTO.setBio(null);
                userDTO.setEmail(null);
            }
        }

        return userDTO;
    }

    /**
     * CACHED: Get user by ID with Redis caching (30min TTL)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Cacheable(value = "usersById", key = "#userId", unless = "#result == null")
    public User getUserById(Long userId) {
        log.debug("🔍 Cache MISS: Loading User entity for ID: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * CACHED: Get user by username with Redis caching (30min TTL)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @Cacheable(value = "usersByUsername", key = "#username", unless = "#result == null")
    public User getUserByUsername(String username) {
        log.debug("🔍 Cache MISS: Loading User entity for username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Check if user exists
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    public boolean userExists(Long userId) {
        return userRepository.existsById(userId);
    }

    /**
     * Generate new access token from refresh token
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true, fallbackToMaster = true)
    public String generateNewAccessToken(String refreshToken) {
        RefreshToken storedRefreshToken = refreshTokenService.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(storedRefreshToken);
        User user = verifiedToken.getUser();
        return jwtService.generateToken(user);
    }
}