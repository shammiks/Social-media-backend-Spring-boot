package com.example.DPMHC_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
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


    private boolean isAdmin;

    private Date createdAt;

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
