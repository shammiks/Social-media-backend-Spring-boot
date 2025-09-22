package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    // ======================= OPTIMIZED QUERIES WITH JOIN FETCH =======================
    
    // Optimized: Get comments with user data in single query
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user " +
           "WHERE c.post = :post " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findAllByPostWithUser(@Param("post") Post post);
    
    // Optimized: Top-level comments with user data
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt DESC")
    Page<Comment> findTopLevelCommentsWithUser(@Param("postId") Long postId, Pageable pageable);
    
    // Optimized: All comments for a post with user data
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user " +
           "WHERE c.post.id = :postId " +
           "ORDER BY c.createdAt DESC")
    Page<Comment> findByPostIdWithUser(@Param("postId") Long postId, Pageable pageable);
    
    // Optimized: Replies for a parent comment with user data
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user " +
           "WHERE c.parentComment = :parentComment " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findRepliesWithUser(@Param("parentComment") Comment parentComment);
    
    // ======================= LEGACY METHODS (Keep for backward compatibility) =======================
    List<Comment> findAllByPost(Post post);

    // Find only top-level comments (no parent comment) for a post
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    Page<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(@Param("postId") Long postId, Pageable pageable);

    // Find all comments (including replies) for a post
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt DESC")
    Page<Comment> findByPostIdOrderByCreatedAtDesc(@Param("postId") Long postId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    // Find replies for a parent comment
    List<Comment> findByParentCommentOrderByCreatedAtAsc(Comment parentComment);

    // Count total comments for a specific post (including replies)
    long countByPost(Post post);

    // Count only top-level comments for a post
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post = :post AND c.parentComment IS NULL")
    long countByPostAndParentCommentIsNull(@Param("post") Post post);
    
    // ======================= BATCH OPTIMIZED QUERIES =======================
    
    // Batch query to get comment counts for multiple posts
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> getCommentCountsByPostIds(@Param("postIds") List<Long> postIds);
    
    // Batch query to get top-level comment counts for multiple posts
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds AND c.parentComment IS NULL GROUP BY c.post.id")
    List<Object[]> getTopLevelCommentCountsByPostIds(@Param("postIds") List<Long> postIds);
}