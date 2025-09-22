package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.model.Like;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.LikeRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    public void toggleLike(Long postId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (likeRepository.existsByUserAndPost(user, post)) {
            likeRepository.deleteByUserAndPost(user, post); // unlike
        } else {
            likeRepository.save(Like.builder().user(user).post(post).build()); // like
        }
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    public long getLikeCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return likeRepository.countByPost(post);
    }
}
