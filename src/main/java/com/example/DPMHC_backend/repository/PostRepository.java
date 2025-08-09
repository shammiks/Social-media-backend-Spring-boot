package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByUser(User user);
    List<Post> findByIsPublicTrueOrderByCreatedAtDesc();    // Explore Feed
    List<Post> findByUserInOrderByCreatedAtDesc(List<User> users);  // Home Feed

    // Optional: More efficient queries that avoid lazy loading issues
    @Query("SELECT p FROM Post p JOIN FETCH p.user ORDER BY p.createdAt DESC")
    Page<Post> findAllWithUser(Pageable pageable);

    // Method to update likes count directly in database
    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + :increment WHERE p.id = :postId")
    void updateLikesCount(@Param("postId") Long postId, @Param("increment") int increment);

    // Method to sync likes count with actual likes (for data consistency)
    @Modifying
    @Query("UPDATE Post p SET p.likesCount = (SELECT COUNT(l) FROM Like l WHERE l.post = p) WHERE p.id = :postId")
    void syncLikesCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = (SELECT COUNT(l) FROM Like l WHERE l.post = p)")
    void syncAllLikesCount();

    Page<Post> findByUser(User user, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN p.bookmarks b WHERE b.user = :user ORDER BY b.createdAt DESC")
    Page<Post> findBookmarkedPostsByUser(@Param("user") User user, Pageable pageable);

    List<Post> findByUser(User user);
}