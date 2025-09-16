package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.Bookmark;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.BookmarkRepository;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService; // To reuse mapToDTO method

    @Transactional
    public BookmarkResponse toggleBookmark(Long postId, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Bookmark> existing = bookmarkRepository.findByUserAndPost(user, post);
        boolean isBookmarked;

        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get()); // Remove bookmark
            isBookmarked = false;
        } else {
            Bookmark bookmark = Bookmark.builder()
                    .user(user)
                    .post(post)
                    .createdAt(new Date())
                    .build();
            bookmarkRepository.save(bookmark); // Add bookmark
            isBookmarked = true;
        }

        return new BookmarkResponse(isBookmarked);
    }

    @Transactional(readOnly = true)
    public boolean isPostBookmarkedByUser(Long postId, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookmarkRepository.existsByUserAndPost(user, post);
    }

    @Transactional(readOnly = true)
    public List<PostDTO> getUserBookmarks(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Use optimized query with JOIN FETCH to avoid N+1
        List<Bookmark> bookmarks = bookmarkRepository.findByUserWithPostDetailsOrderByCreatedAtDesc(user);

        // Extract posts and use batch mapping
        List<Post> posts = bookmarks.stream()
                .map(Bookmark::getPost)
                .collect(java.util.stream.Collectors.toList());

        return postService.mapToDTOs(posts, userEmail);
    }


    @Transactional(readOnly = true)
    public Page<PostDTO> getUserBookmarks(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Use optimized query with JOIN FETCH to avoid N+1
        Page<Bookmark> bookmarks = bookmarkRepository.findByUserWithPostDetailsOrderByCreatedAtDesc(user, pageable);

        // Extract posts and use batch mapping for the page content
        List<Post> posts = bookmarks.getContent().stream()
                .map(Bookmark::getPost)
                .collect(java.util.stream.Collectors.toList());

        List<PostDTO> optimizedDTOs = postService.mapToDTOs(posts, userEmail);
        
        return new org.springframework.data.domain.PageImpl<>(
            optimizedDTOs, 
            pageable, 
            bookmarks.getTotalElements()
        );
    }

    @Data
    @AllArgsConstructor
    public static class BookmarkResponse {
        private boolean isBookmarked;
    }
}