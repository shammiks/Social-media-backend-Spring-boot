package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Like;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    boolean existsByUserAndPost(User user, Post post);
    boolean existsByPostAndUserEmail(Post post, String email);
    Optional<Like> findByPostAndUser(Post post, User user);
    long countByPost(Post post);
    void deleteByUserAndPost(User user, Post post);
}
