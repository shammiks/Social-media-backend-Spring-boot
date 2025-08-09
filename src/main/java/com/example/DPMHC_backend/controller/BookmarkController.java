package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.BookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/{postId}")
    public ResponseEntity<BookmarkService.BookmarkResponse> toggleBookmark(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user) {

        BookmarkService.BookmarkResponse response = bookmarkService.toggleBookmark(postId, user.getEmail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{postId}/status")
    public ResponseEntity<Boolean> getBookmarkStatus(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user) {

        boolean isBookmarked = bookmarkService.isPostBookmarkedByUser(postId, user.getEmail());
        return ResponseEntity.ok(isBookmarked);
    }

    @GetMapping("/my-bookmarks")
    public ResponseEntity<List<PostDTO>> getUserBookmarks(@AuthenticationPrincipal User user) {
        List<PostDTO> bookmarks = bookmarkService.getUserBookmarks(user.getEmail());
        return ResponseEntity.ok(bookmarks);
    }

    @GetMapping("/my-bookmarks-paginated")
    public ResponseEntity<Page<PostDTO>> getUserBookmarksPaginated(
            @AuthenticationPrincipal User user,
            Pageable pageable) {

        Page<PostDTO> bookmarks = bookmarkService.getUserBookmarks(user.getEmail(), pageable);
        return ResponseEntity.ok(bookmarks);
    }
}