package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "user_warnings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = true)
    private Post post; // The post that caused the warning (optional)

    @Column(nullable = false)
    private String reason;

    @Column(name = "warning_message", length = 1000)
    private String warningMessage;

    @Column(name = "issued_by")
    private Long issuedBy; // Admin user ID who issued the warning

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }
}