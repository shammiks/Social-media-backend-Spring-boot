package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    
    // ======================= OPTIMIZED QUERIES WITH JOIN FETCH =======================
    
    // Optimized: Fetch posts with user data in single query (eliminates N+1 for users)
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findAllWithUser(Pageable pageable);
    
    // Optimized: Get post by ID with all related data
    @Query("SELECT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "WHERE p.id = :id")
    Optional<Post> findByIdWithUser(@Param("id") Long id);
    
    // Optimized: User's posts with user data
    @Query("SELECT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "WHERE p.user = :user " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findByUserWithUser(@Param("user") User user, Pageable pageable);
    
    // Optimized: Public feed with user data
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "WHERE p.isPublic = true " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findPublicPostsWithUser(Pageable pageable);
    
    // Optimized: Home feed for followed users with user data
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "WHERE p.user IN :users " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findByUsersWithUser(@Param("users") List<User> users, Pageable pageable);
    
    // ======================= ENTITY GRAPH OPTIMIZED QUERIES =======================
    
    // Use EntityGraph for optimized loading
    @EntityGraph("Post.withUser")
    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC")
    Page<Post> findAllWithUserGraph(Pageable pageable);
    
    @EntityGraph("Post.withUser")
    @Query("SELECT p FROM Post p WHERE p.isPublic = true ORDER BY p.createdAt DESC")
    Page<Post> findPublicPostsWithUserGraph(Pageable pageable);
    
    @EntityGraph("Post.withUserAndComments")
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdWithUserAndComments(@Param("id") Long id);
    
    // ======================= LEGACY METHODS (Keep for backward compatibility) =======================
    List<Post> findAllByUser(User user);
    List<Post> findByIsPublicTrueOrderByCreatedAtDesc();    
    List<Post> findByUserInOrderByCreatedAtDesc(List<User> users);

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

    // Optimized: Bookmarked posts with user data
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.user " +
           "JOIN p.bookmarks b " +
           "WHERE b.user = :user " +
           "ORDER BY b.createdAt DESC")
    Page<Post> findBookmarkedPostsByUserWithUser(@Param("user") User user, Pageable pageable);
    
    // Legacy method
    @Query("SELECT p FROM Post p JOIN p.bookmarks b WHERE b.user = :user ORDER BY b.createdAt DESC")
    Page<Post> findBookmarkedPostsByUser(@Param("user") User user, Pageable pageable);

    List<Post> findByUser(User user);
}