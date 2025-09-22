package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "bookmarks", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "post_id"})
    },
    indexes = {
        @Index(name = "idx_bookmark_user_id", columnList = "user_id"),
        @Index(name = "idx_bookmark_post_id", columnList = "post_id"),
        @Index(name = "idx_bookmark_created_at", columnList = "createdAt"),
        @Index(name = "idx_bookmark_user_created", columnList = "user_id, createdAt")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    private Date createdAt;
}