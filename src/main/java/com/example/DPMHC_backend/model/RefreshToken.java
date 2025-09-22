package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_token", columnList = "token"),
    @Index(name = "idx_refresh_token_expiry", columnList = "expiryDate"),
    @Index(name = "idx_refresh_token_user_active", columnList = "user_id, isRevoked"),
    @Index(name = "idx_refresh_token_cleanup", columnList = "expiryDate, isRevoked")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private boolean isRevoked = false;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    public void revoke() {
        this.isRevoked = true;
        this.revokedAt = LocalDateTime.now();
    }
}
