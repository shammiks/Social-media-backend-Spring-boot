package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    @ManyToOne
    private User user;

    private Date expiryDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean verified = false;
}
