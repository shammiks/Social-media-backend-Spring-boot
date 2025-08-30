package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.CommentLike;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);

    boolean existsByCommentAndUser(Comment comment, User user);

    long countByComment(Comment comment);

    void deleteByCommentAndUser(Comment comment, User user);
}
