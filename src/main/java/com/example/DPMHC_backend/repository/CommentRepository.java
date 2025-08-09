package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Bookmark;
import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByPost(Post post);

    Page<Bookmark> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Bookmark> findByUserOrderByCreatedAtDesc(User user);

    Optional<Bookmark> findByUserAndPost(User user, Post post);
    boolean existsByUserAndPost(User user, Post post);

    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt DESC")
    Page<Comment> findByPostIdOrderByCreatedAtDesc(@Param("postId") Long postId, Pageable pageable);

    // Count comments for a specific post
    long countByPost(Post post);

    // Find comments with user details (to avoid N+1 queries)
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId ORDER BY c.createdAt DESC")
    List<Comment> findByPostIdWithUserOrderByCreatedAtDesc(@Param("postId") Long postId);
}