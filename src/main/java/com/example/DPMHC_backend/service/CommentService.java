package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import com.example.DPMHC_backend.dto.CommentDTO;
import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.CommentLike;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.CommentLikeRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService; // Add notification service

    @WriteDB(type = WriteDB.OperationType.CREATE)
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

        Comment savedComment = commentRepository.save(comment);

        // Trigger notification for new comment
        notificationService.handleComment(post.getUser().getEmail(), userEmail, postId, savedComment.getId());

        return savedComment;
    }

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    public Comment editComment(Long commentId, String content, String userEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to edit this comment.");
        }

        comment.setContent(content);
        comment.setUpdatedAt(new Date());

        return commentRepository.save(comment);
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<CommentDTO> getComments(Long postId, Pageable pageable) {
        // Only get top-level comments (no parent), replies will be fetched separately
        return commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(postId, pageable)
                .map(comment -> {
                    // For public API, we'll use null user context for now
                    // In the future, this could be improved to get current user from security context
                    return mapToDTO(comment, null, true);
                });
    }
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + commentId));
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<CommentDTO> getCommentsWithUser(Long postId, Pageable pageable, String userEmail) {
        DatabaseContextHolder.setUserContext(userEmail);
        final User currentUser = userEmail != null ?
            userRepository.findByEmail(userEmail).orElse(null) : null;

        return commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(postId, pageable)
                .map(comment -> mapToDTO(comment, currentUser, true));
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CommentDTO> getCommentsByPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        return commentRepository.findAllByPost(post).stream()
                .map(comment -> mapToDTO(comment, null, true))
                .toList();
    }

    @WriteDB(type = WriteDB.OperationType.DELETE)
    @Transactional
    public void deleteComment(Long commentId, String userEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to delete this comment.");
        }

        commentRepository.delete(comment);
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    public Comment addReply(Long parentCommentId, String content, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));

        Comment reply = Comment.builder()
                .content(content)
                .user(user)
                .post(parentComment.getPost())
                .parentComment(parentComment)
                .createdAt(new Date())
                .build();

        Comment savedReply = commentRepository.save(reply);

        // Trigger notification for comment reply
        notificationService.handleCommentReply(parentComment.getUser().getEmail(), userEmail,
                                              parentCommentId, savedReply.getId());

        return savedReply;
    }

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    public boolean toggleCommentLike(Long commentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (commentLikeRepository.existsByCommentAndUser(comment, user)) {
            // Unlike - remove the like
            commentLikeRepository.deleteByCommentAndUser(comment, user);
            return false; // unliked
        } else {
            // Like - add the like
            CommentLike commentLike = CommentLike.builder()
                    .comment(comment)
                    .user(user)
                    .createdAt(new Date())
                    .build();
            commentLikeRepository.save(commentLike);

            // Trigger notification for comment like
            notificationService.handleCommentLike(comment.getUser().getEmail(), userEmail, commentId);

            return true; // liked
        }
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CommentDTO> getReplies(Long parentCommentId, String currentUserEmail) {
        DatabaseContextHolder.setUserContext(currentUserEmail);
        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));

        final User currentUser = currentUserEmail != null ?
            userRepository.findByEmail(currentUserEmail).orElse(null) : null;

        return parentComment.getReplies().stream()
                .map(reply -> mapToDTO(reply, currentUser, false)) // Don't include nested replies
                .collect(Collectors.toList());
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());


    private CommentDTO mapToDTO(Comment comment, User currentUser, boolean includeReplies) {
        final User finalCurrentUser = currentUser; // Make it effectively final for lambda expressions

        CommentDTO.CommentDTOBuilder builder = CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(FORMATTER.format(comment.getCreatedAt().toInstant()))
                .updatedAt(comment.getUpdatedAt() != null ?
                    FORMATTER.format(comment.getUpdatedAt().toInstant()) : null)
                .edited(comment.getUpdatedAt() != null)
                .postId(comment.getPost().getId())
                .username(comment.getUser().getUsername())
                .userId(comment.getUser().getId())
                .avatar(comment.getUser().getAvatar())
                .profileImageUrl(comment.getUser().getProfileImageUrl())
                .parentCommentId(comment.getParentComment() != null ?
                    comment.getParentComment().getId() : null)
                .replyCount(comment.getReplies().size())
                .likeCount(comment.getLikes().size());

        // Check if current user liked this comment
        if (finalCurrentUser != null) {
            boolean likedByUser = comment.getLikes().stream()
                    .anyMatch(like -> like.getUser().getId().equals(finalCurrentUser.getId()));
            builder.likedByCurrentUser(likedByUser);
        } else {
            builder.likedByCurrentUser(false);
        }

        // Include replies if requested (for top-level comments)
        if (includeReplies && comment.getParentComment() == null) {
            List<CommentDTO> replyDTOs = comment.getReplies().stream()
                    .map(reply -> mapToDTO(reply, finalCurrentUser, false))
                    .collect(Collectors.toList());
            builder.replies(replyDTOs);
        }

        return builder.build();
    }

    // Update existing mapToDTO method
    private CommentDTO mapToDTO(Comment comment) {
        return mapToDTO(comment, null, true);
    }
}
