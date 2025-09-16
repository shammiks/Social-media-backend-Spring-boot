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

    // OPTIMIZED BATCH QUERIES TO ELIMINATE N+1 PROBLEMS
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p IN :posts")
    List<Post> findPostsWithUsers(@Param("posts") List<Post> posts);

    // Get all posts with user details for feed (eliminates user N+1)
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.isPublic = true ORDER BY p.createdAt DESC")
    List<Post> findPublicPostsWithUsers();

    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.user IN :users ORDER BY p.createdAt DESC")
    List<Post> findByUsersWithUserDetails(@Param("users") List<User> users);

    // PROJECTION QUERIES FOR LIGHTWEIGHT DATA TRANSFER
    @Query("SELECT p.id as id, p.content as content, p.imageUrl as imageUrl, p.videoUrl as videoUrl, " +
           "p.pdfUrl as pdfUrl, p.isPublic as isPublic, p.likesCount as likesCount, p.createdAt as createdAt, " +
           "u.username as username, u.id as userId, u.avatar as avatar, u.profileImageUrl as profileImageUrl " +
           "FROM Post p JOIN p.user u " +
           "WHERE p.isPublic = true ORDER BY p.createdAt DESC")
    List<com.example.DPMHC_backend.dto.projection.PostFeedProjection> findPublicPostsProjection();

    @Query("SELECT p.id as id, p.content as content, p.imageUrl as imageUrl, p.videoUrl as videoUrl, " +
           "p.pdfUrl as pdfUrl, p.isPublic as isPublic, p.likesCount as likesCount, p.createdAt as createdAt, " +
           "u.username as username, u.id as userId, u.avatar as avatar, u.profileImageUrl as profileImageUrl " +
           "FROM Post p JOIN p.user u " +
           "WHERE u.id = :userId ORDER BY p.createdAt DESC")
    List<com.example.DPMHC_backend.dto.projection.PostFeedProjection> findUserPostsProjection(@Param("userId") Long userId);
}