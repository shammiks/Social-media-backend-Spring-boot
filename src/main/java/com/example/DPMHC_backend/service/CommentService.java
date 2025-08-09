package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.CommentDTO;
import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.CommentRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Comment addComment(Long postId, String content, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Comment comment = Comment.builder()
                .content(content)
                .user(user)
                .post(post)
                .createdAt(new Date())
                .build();

        return commentRepository.save(comment);
    }

    public Page<CommentDTO> getComments(Long postId, Pageable pageable) {
        return commentRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable)
                .map(this::mapToDTO);
    }


    public List<CommentDTO> getCommentsByPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        return commentRepository.findAllByPost(post).stream().map(comment -> CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().toString())
                .username(comment.getUser().getUsername()) // 👈 here
                .build()
        ).toList();
    }

    public void deleteComment(Long commentId, String userEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to delete this comment.");
        }

        commentRepository.delete(comment);
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());


    private CommentDTO mapToDTO(Comment comment) {
        return CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(FORMATTER.format(comment.getCreatedAt().toInstant()))
                .postId(comment.getPost().getId())
                .username(comment.getUser().getUsername())
                .userId(comment.getUser().getId())
                .build();
    }

}
