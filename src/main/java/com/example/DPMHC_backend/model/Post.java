package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_created_at", columnList = "createdAt"),
    @Index(name = "idx_post_user_id", columnList = "user_id"),
    @Index(name = "idx_post_public_created", columnList = "isPublic, createdAt"),
    @Index(name = "idx_post_reported", columnList = "reported")
})
@NamedEntityGraph(
    name = "Post.withUser",
    attributeNodes = @NamedAttributeNode("user")
)
@NamedEntityGraph(
    name = "Post.withUserAndComments",
    attributeNodes = {
        @NamedAttributeNode("user"),
        @NamedAttributeNode(value = "comments", subgraph = "comments.user")
    },
    subgraphs = @NamedSubgraph(
        name = "comments.user",
        attributeNodes = @NamedAttributeNode("user")
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content; // Text (optional)

    private String imageUrl; // Image URL (optional)

    private String videoUrl; // Video URL (optional)

    @Column(nullable = true)
    private Boolean isPublic; // Can be null (optional)

    // Custom getter that defaults to false if null
    public boolean isPublic() {
        return Boolean.TRUE.equals(this.isPublic);
    }

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Like> likes = new HashSet<>();

    // Add this method to safely get likes count
    // Use this field instead for like count
    @Column(nullable = false, columnDefinition = "int default 0")
    private int likesCount = 0;

    // Add this method to get the actual likes collection when needed
    public Set<Like> getLikesSet() {
        return likes;
    }

    @ManyToMany
    @JoinTable(
            name = "post_likes",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )

    private Set<User> likedBy = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Bookmark> bookmarks = new HashSet<>();

    private String pdfUrl;

    private Date createdAt;

    private boolean reported = false;

    private Date updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();
}