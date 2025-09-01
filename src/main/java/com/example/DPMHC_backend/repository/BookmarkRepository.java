package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Bookmark;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUserAndPost(User user, Post post);

    boolean existsByPostAndUserEmail(Post post, String email);

    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    List<Bookmark> findByUserOrderByCreatedAtDesc(User user);

    Page<Bookmark> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndPost(User user, Post post);

    long countByPost(Post post);


    // Get bookmarked posts with user details to avoid N+1 queries
    @Query("SELECT b FROM Bookmark b JOIN FETCH b.post p JOIN FETCH p.user WHERE b.user = :user ORDER BY b.createdAt DESC")
    List<Bookmark> findByUserWithPostDetailsOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.post p JOIN FETCH p.user WHERE b.user = :user ORDER BY b.createdAt DESC")
    Page<Bookmark> findByUserWithPostDetailsOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);
}