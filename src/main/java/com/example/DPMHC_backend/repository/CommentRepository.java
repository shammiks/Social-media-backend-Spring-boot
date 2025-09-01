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
}