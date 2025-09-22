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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService; // Add notification service

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "post-comments", key = "#postId + ':*'", allEntries = false),
        @CacheEvict(value = "comment-counts", key = "#postId"),
        @CacheEvict(value = "posts", key = "#postId") // Evict post cache as comment count changed
    })
    public Comment addComment(Long postId, String content, String userEmail) {
        log.debug("💬 Adding comment to post {} by user {}", postId, userEmail);
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
    @Caching(evict = {
        @CacheEvict(value = "post-comments", key = "#result.post.id + ':*'", allEntries = false),
        @CacheEvict(value = "comment-replies", key = "#result.parentComment?.id + ':*'", allEntries = false)
    })
    public Comment editComment(Long commentId, String content, String userEmail) {
        log.debug("✏️ Editing comment {} by user {}", commentId, userEmail);
        
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to edit this comment.");
        }

        comment.setContent(content);
        comment.setUpdatedAt(new Date());
        Comment savedComment = commentRepository.save(comment);
        
        log.debug("✅ Comment {} edited successfully", commentId);
        return savedComment;
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = "post-comments", key = "#postId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize", 
               condition = "#pageable.pageNumber == 0") // Only cache first page
    public Page<CommentDTO> getComments(Long postId, Pageable pageable) {
        log.debug("📖 Getting comments for post {} (page: {}, size: {})", 
                 postId, pageable.getPageNumber(), pageable.getPageSize());
        
        // Only get top-level comments (no parent), replies will be fetched separately
        Page<CommentDTO> comments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(postId, pageable)
                .map(comment -> {
                    // For public API, we'll use null user context for now
                    // In the future, this could be improved to get current user from security context
                    return mapToDTO(comment, null, true);
                });
        
        log.debug("✅ Retrieved {} comments for post {}", comments.getContent().size(), postId);
        return comments;
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
    @Cacheable(value = "post-comments", key = "#postId + ':all'")
    public List<CommentDTO> getCommentsByPost(Long postId) {
        log.debug("📝 Getting all comments for post {}", postId);
        
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        List<CommentDTO> comments = commentRepository.findAllByPost(post).stream()
                .map(comment -> mapToDTO(comment, null, true))
                .toList();
        
        log.debug("✅ Retrieved {} total comments for post {}", comments.size(), postId);
        return comments;
    }

    /**
     * Get comment count for a post (cached)
     */
    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = "comment-counts", key = "#postId")
    public long getCommentCount(Long postId) {
        log.debug("🔢 Getting comment count for post {}", postId);
        
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        long count = commentRepository.countByPost(post);
        log.debug("📊 Post {} has {} comments", postId, count);
        return count;
    }

    @WriteDB(type = WriteDB.OperationType.DELETE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "post-comments", key = "#result.post.id + ':*'", allEntries = false),
        @CacheEvict(value = "comment-counts", key = "#result.post.id"),
        @CacheEvict(value = "comment-replies", key = "#commentId + ':*'", allEntries = false),
        @CacheEvict(value = "posts", key = "#result.post.id")
    })
    public Comment deleteComment(Long commentId, String userEmail) {
        log.debug("🗑️ Deleting comment {} by user {}", commentId, userEmail);
        
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("You are not authorized to delete this comment.");
        }

        commentRepository.delete(comment);
        log.debug("✅ Comment {} deleted successfully", commentId);
        
        return comment; // Return for cache eviction
    }

    @WriteDB(type = WriteDB.OperationType.CREATE)
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "comment-replies", key = "#parentCommentId + ':*'", allEntries = false),
        @CacheEvict(value = "post-comments", key = "#result.post.id + ':*'", allEntries = false),
        @CacheEvict(value = "comment-counts", key = "#result.post.id")
    })
    public Comment addReply(Long parentCommentId, String content, String userEmail) {
        log.debug("➕ Adding reply to comment {} by user {}", parentCommentId, userEmail);
        
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

        log.debug("✅ Reply added successfully to comment {}", parentCommentId);
        return savedReply;
    }

    @WriteDB(type = WriteDB.OperationType.UPDATE)
    @Transactional
    @CacheEvict(value = "comment-likes", key = "#commentId + ':*'", allEntries = false)
    public boolean toggleCommentLike(Long commentId, String userEmail) {
        log.debug("👍 Toggling like for comment {} by user {}", commentId, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (commentLikeRepository.existsByCommentAndUser(comment, user)) {
            // Unlike - remove the like
            commentLikeRepository.deleteByCommentAndUser(comment, user);
            log.debug("👎 User {} unliked comment {}", userEmail, commentId);
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

            log.debug("👍 User {} liked comment {}", userEmail, commentId);
            return true; // liked
        }
    }

    @ReadOnlyDB(strategy = ReadOnlyDB.LoadBalanceStrategy.USER_SPECIFIC, userSpecific = true)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = "comment-replies", key = "#parentCommentId + ':user:' + (#currentUserEmail ?: 'anonymous')", 
               unless = "#result == null || #result.isEmpty()")
    public List<CommentDTO> getReplies(Long parentCommentId, String currentUserEmail) {
        log.debug("📝 Fetching replies for comment {} by user {}", parentCommentId, currentUserEmail);
        
        DatabaseContextHolder.setUserContext(currentUserEmail);
        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));

        final User currentUser = currentUserEmail != null ?
            userRepository.findByEmail(currentUserEmail).orElse(null) : null;

        List<CommentDTO> replies = parentComment.getReplies().stream()
                .map(reply -> mapToDTO(reply, currentUser, false)) // Don't include nested replies
                .collect(Collectors.toList());
        
        log.debug("✅ Retrieved {} replies for comment {}", replies.size(), parentCommentId);
        return replies;
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
