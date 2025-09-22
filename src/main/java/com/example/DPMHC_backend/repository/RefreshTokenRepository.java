package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.RefreshToken;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    Optional<RefreshToken> findByToken(String token);
    
    Optional<RefreshToken> findByTokenAndIsRevokedFalse(String token);
    
    List<RefreshToken> findByUserAndIsRevokedFalse(User user);
    
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true, rt.revokedAt = :revokedAt WHERE rt.user = :user")
    void revokeAllByUser(@Param("user") User user, @Param("revokedAt") LocalDateTime revokedAt);
    
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user = :user AND rt.isRevoked = false")
    long countActiveTokensByUser(@Param("user") User user);

    // ======================= OPTIMIZED BATCH CLEANUP QUERIES =======================
    
    /**
     * OPTIMIZED: Batch delete expired tokens with LIMIT to avoid long-running transactions
     */
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE expiry_date < :expiryDate LIMIT :batchSize", 
           nativeQuery = true)
    int deleteExpiredTokensBatch(@Param("expiryDate") LocalDateTime expiryDate, 
                                @Param("batchSize") int batchSize);
    
    /**
     * OPTIMIZED: Batch delete revoked tokens older than specified date
     */
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE is_revoked = true AND revoked_at < :revokedBefore LIMIT :batchSize", 
           nativeQuery = true)
    int deleteRevokedTokensBatch(@Param("revokedBefore") LocalDateTime revokedBefore, 
                                @Param("batchSize") int batchSize);
    
    /**
     * Count expired tokens for monitoring
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.expiryDate < :now")
    long countExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Count revoked tokens for monitoring
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = true")
    long countRevokedTokens();
}
