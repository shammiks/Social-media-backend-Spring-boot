package com.example.DPMHC_backend.model;

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

    private String bio;

    @ManyToMany(mappedBy = "likedBy")
    private Set<Post> likedPosts = new HashSet<>();


    private boolean isAdmin;

    private Date createdAt;

    private Date updatedAt;


}
