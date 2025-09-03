package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.model.RefreshToken;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration:2592000000}") // 30 days in milliseconds
    private long refreshTokenExpiration;

    @Value("${app.refresh-token.max-per-user:5}") // Maximum refresh tokens per user
    private int maxTokensPerUser;

    /**
     * Create a new refresh token for a user
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        log.info("Creating refresh token for user: {}", user.getEmail());
        
        // Check if user has reached maximum tokens limit
        long activeTokenCount = refreshTokenRepository.countActiveTokensByUser(user);
        if (activeTokenCount >= maxTokensPerUser) {
            log.info("User {} has reached maximum refresh tokens limit, revoking oldest", user.getEmail());
            revokeOldestTokenForUser(user);
        }

        // Create new refresh token
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token with ID: {} for user: {}", refreshToken.getId(), user.getEmail());
        
        return refreshToken;
    }

    /**
     * Find refresh token by token string
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByTokenAndIsRevokedFalse(token);
    }

    /**
     * Verify if refresh token is valid (not expired and not revoked)
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isExpired()) {
            log.warn("Refresh token {} has expired", token.getToken());
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        
        if (token.isRevoked()) {
            log.warn("Refresh token {} has been revoked", token.getToken());
            throw new RuntimeException("Refresh token is revoked. Please make a new signin request");
        }
        
        return token;
    }

    /**
     * Revoke a specific refresh token
     */
    @Transactional
    public void revokeToken(String token) {
        log.info("Revoking refresh token: {}", token);
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.save(refreshToken);
                    log.info("Successfully revoked refresh token: {}", token);
                });
    }

    /**
     * Revoke all refresh tokens for a user (useful for logout from all devices)
     */
    @Transactional
    public void revokeAllTokensForUser(User user) {
        log.info("Revoking all refresh tokens for user: {}", user.getEmail());
        refreshTokenRepository.revokeAllByUser(user, LocalDateTime.now());
    }

    /**
     * Revoke the oldest token for a user when they reach the limit
     */
    @Transactional
    protected void revokeOldestTokenForUser(User user) {
        refreshTokenRepository.findByUserAndIsRevokedFalse(user)
                .stream()
                .min((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                .ifPresent(oldestToken -> {
                    oldestToken.revoke();
                    refreshTokenRepository.save(oldestToken);
                    log.info("Revoked oldest refresh token for user: {}", user.getEmail());
                });
    }

    /**
     * Cleanup expired tokens (scheduled task)
     */
    @Scheduled(fixedDelay = 86400000) // Run every 24 hours
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired refresh tokens");
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Cleaned up expired refresh tokens");
    }

    /**
     * Get active tokens count for a user
     */
    public long getActiveTokensCount(User user) {
        return refreshTokenRepository.countActiveTokensByUser(user);
    }
}
