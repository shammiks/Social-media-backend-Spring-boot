package com.example.DPMHC_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_banned", columnList = "banned"),
    @Index(name = "idx_user_admin", columnList = "isAdmin"),
    @Index(name = "idx_user_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("username")
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    private String email;

    @Column(name = "verification_token")
    private String verificationToken;
    private boolean isEmailVerified = false;


    // In User.java
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private boolean banned = false;

    private String password;

    private String avatar;


    @Column(name = "profile_image_url")
    private String profileImageUrl;

    private String bio;

    @ManyToMany(mappedBy = "likedBy")
    private Set<Post> likedPosts = new HashSet<>();

    @Column(name = "isAdmin", nullable = false, columnDefinition = "BIT DEFAULT 0")
    private boolean isAdmin = false;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
        // Don't include likedPosts or other lazy collections in toString()
    }

}
