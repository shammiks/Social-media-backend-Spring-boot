package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Like;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    @Modifying
    @Query("DELETE FROM Like l WHERE l.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
    
    // ======================= OPTIMIZED SINGLE QUERIES =======================
    boolean existsByUserAndPost(User user, Post post);
    boolean existsByPostAndUserEmail(Post post, String email);
    Optional<Like> findByPostAndUser(Post post, User user);
    long countByPost(Post post);
    void deleteByUserAndPost(User user, Post post);
    
    // ======================= BATCH OPTIMIZED QUERIES =======================
    
    // Batch query to get like counts for multiple posts
    @Query("SELECT l.post.id, COUNT(l) FROM Like l WHERE l.post.id IN :postIds GROUP BY l.post.id")
    List<Object[]> getLikeCountsByPostIds(@Param("postIds") List<Long> postIds);
    
    // Batch query to check if user liked multiple posts
    @Query("SELECT l.post.id FROM Like l WHERE l.post.id IN :postIds AND l.user.email = :userEmail")
    List<Long> getLikedPostIdsByUser(@Param("postIds") List<Long> postIds, @Param("userEmail") String userEmail);
}
