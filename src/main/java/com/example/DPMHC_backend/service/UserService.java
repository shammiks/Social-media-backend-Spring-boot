package com.example.DPMHC_backend.service;
import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.PasswordResetToken;
import com.example.DPMHC_backend.model.Role;
import com.example.DPMHC_backend.dto.UserDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.PasswordResetTokenRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import com.example.DPMHC_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.base.url}")
    private String baseUrl;


    private final List<String> allowedDomains = List.of("gmail.com", "yahoo.com", "outlook.com");
    private void validateEmailDomain(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (!allowedDomains.contains(domain)) {
            throw new RuntimeException("Only Gmail, Yahoo, and Outlook email addresses are allowed.");
        }
    }

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


    public String login(String email, String password) {
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


    public String sendPasswordResetLink(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No user found with this email"));

        tokenRepository.deleteByUser(user);
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(new Date(System.currentTimeMillis() + 1000 * 60 * 30)) // 30 minutes
                .build();

        resetTokenRepository.save(resetToken);

        String resetLink = baseUrl + "/api/auth/reset-password-form?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        return "Reset link sent to your email";
    }

    public String resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (resetToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(new Date());

        userRepository.save(user);
        resetTokenRepository.delete(resetToken); // remove token after use

        return "Password reset successful.";
    }



    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToDTO(user);
    }

    public User getUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDTO updateAvatar(MultipartFile file, String email) throws IOException {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String imageUrl = cloudinaryService.uploadFile(file, "avatars");
        user.setAvatar(imageUrl);
        userRepository.save(user);
        return mapToDTO(user);
    }

    @Transactional
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
}