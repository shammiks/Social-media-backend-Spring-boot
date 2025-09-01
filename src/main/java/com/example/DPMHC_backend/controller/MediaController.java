package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.MediaUploadDTO;
import com.example.DPMHC_backend.model.MediaFile;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final MediaService mediaService;

    /**
     * Upload media file
     */
    @PostMapping("/upload")
    public ResponseEntity<MediaUploadDTO> uploadMedia(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            Long userId = getUserIdFromAuth(authentication);
            MediaUploadDTO uploadedFile = mediaService.uploadMedia(file, userId);

            return ResponseEntity.status(HttpStatus.CREATED).body(uploadedFile);

        } catch (IOException e) {
            log.error("Error uploading file: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get media file info by ID
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<MediaUploadDTO> getMediaFile(
            @PathVariable Long fileId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MediaFile mediaFile = mediaService.getMediaFile(fileId, userId);

        MediaUploadDTO dto = new MediaUploadDTO();
        dto.setId(mediaFile.getId());
        dto.setOriginalFilename(mediaFile.getOriginalFilename());
        dto.setFileUrl(mediaFile.getFileUrl());
        dto.setThumbnailUrl(mediaFile.getThumbnailUrl());
        dto.setFileType(mediaFile.getFileType());
        dto.setFileSize(mediaFile.getFileSize());
        dto.setMimeType(mediaFile.getMimeType());
        dto.setWidth(mediaFile.getWidth());
        dto.setHeight(mediaFile.getHeight());
        dto.setDuration(mediaFile.getDuration());
        dto.setCloudinaryPublicId(mediaFile.getCloudinaryPublicId());
        dto.setFileSizeFormatted(mediaFile.getFileSizeFormatted());

        return ResponseEntity.ok(dto);
    }

    /**
     * Download media file
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadMediaFile(
            @PathVariable Long fileId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MediaFile mediaFile = mediaService.getMediaFile(fileId, userId);
        Resource resource = mediaService.downloadMediaFile(fileId, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaFile.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + mediaFile.getOriginalFilename() + "\"")
                .body(resource);
    }

    /**
     * Delete media file
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, String>> deleteMediaFile(
            @PathVariable Long fileId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        mediaService.deleteMediaFile(fileId, userId);

        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }

    /**
     * Get user's uploaded files with pagination
     */
    @GetMapping("/my-files")
    public ResponseEntity<Page<MediaUploadDTO>> getUserMediaFiles(
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MediaUploadDTO> mediaFiles = mediaService.getUserMediaFiles(userId, pageable);

        return ResponseEntity.ok(mediaFiles);
    }

    /**
     * Get user's files by type
     */
    @GetMapping("/my-files/type/{fileType}")
    public ResponseEntity<Page<MediaUploadDTO>> getUserMediaFilesByType(
            @PathVariable String fileType,
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MediaUploadDTO> mediaFiles = mediaService.getUserMediaFilesByType(userId, fileType, pageable);

        return ResponseEntity.ok(mediaFiles);
    }

    /**
     * Get user's images
     */
    @GetMapping("/my-files/images")
    public ResponseEntity<Page<MediaUploadDTO>> getUserImages(
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MediaUploadDTO> mediaFiles = mediaService.getUserMediaFilesByType(userId, "image", pageable);

        return ResponseEntity.ok(mediaFiles);
    }

    /**
     * Get user's videos
     */
    @GetMapping("/my-files/videos")
    public ResponseEntity<Page<MediaUploadDTO>> getUserVideos(
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MediaUploadDTO> mediaFiles = mediaService.getUserMediaFilesByType(userId, "video", pageable);

        return ResponseEntity.ok(mediaFiles);
    }

    /**
     * Get user's documents
     */
    @GetMapping("/my-files/documents")
    public ResponseEntity<Page<MediaUploadDTO>> getUserDocuments(
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MediaUploadDTO> mediaFiles = mediaService.getUserMediaFilesByType(userId, "application", pageable);

        return ResponseEntity.ok(mediaFiles);
    }

    /**
     * Get user's storage statistics
     */
    @GetMapping("/storage-stats")
    public ResponseEntity<Map<String, Object>> getStorageStats(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        Map<String, Object> stats = mediaService.getUserStorageStats(userId);

        return ResponseEntity.ok(stats);
    }

    /**
     * Upload multiple files
     */
    @PostMapping("/upload/multiple")
    public ResponseEntity<Map<String, Object>> uploadMultipleMedia(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        try {
            java.util.List<MediaUploadDTO> uploadedFiles = new java.util.ArrayList<>();
            java.util.List<String> errors = new java.util.ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    MediaUploadDTO uploaded = mediaService.uploadMedia(file, userId);
                    uploadedFiles.add(uploaded);
                } catch (Exception e) {
                    errors.add("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
                }
            }

            Map<String, Object> response = Map.of(
                    "uploaded", uploadedFiles,
                    "errors", errors,
                    "totalUploaded", uploadedFiles.size(),
                    "totalErrors", errors.size()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error uploading multiple files: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload files"));
        }
    }

    // Error handling
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        log.error("Media controller error: ", e);
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("Unexpected error in media controller: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }

    // Helper method to extract user ID from authentication
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        throw new RuntimeException("Invalid authentication principal");
    }
}