package com.example.DPMHC_backend.service;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.DPMHC_backend.dto.MediaUploadDTO;
import com.example.DPMHC_backend.model.MediaFile;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.MediaFileRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MediaService {

    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final Cloudinary cloudinary;

    @Value("${app.upload.max-file-size:10485760}") // 10MB default
    private Long maxFileSize;

    @Value("${app.upload.allowed-types}")
    private String allowedTypes;

    private static final List<String> IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    private static final List<String> VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/avi", "video/mov", "video/wmv", "video/webm"
    );

    private static final List<String> AUDIO_TYPES = Arrays.asList(
            "audio/mp3", "audio/wav", "audio/ogg", "audio/m4a", "audio/aac"
    );

    /**
     * Upload media file to Cloudinary
     */
    public MediaUploadDTO uploadMedia(MultipartFile file, Long userId) throws IOException {
        Instant currentTime = Instant.now();
        String currentUTC = currentTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String currentLocal = currentTime.atOffset(ZoneOffset.of("+05:30")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        log.info("Uploading media file for user: {} at UTC: {} | Local IST: {} | System millis: {}", 
                userId, currentUTC, currentLocal, System.currentTimeMillis());

        // Validate file
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String storedFilename = UUID.randomUUID().toString() + fileExtension;

        // Create new Cloudinary instance for each upload to avoid cached timestamps
        Cloudinary freshCloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudinary.config.cloudName,
                "api_key", cloudinary.config.apiKey,
                "api_secret", cloudinary.config.apiSecret,
                "secure", true
        ));

        try {
            // Upload with auto-generated timestamp and extended tolerance
            Map uploadParams = ObjectUtils.asMap(
                    "public_id", "chat_media/" + storedFilename,
                    "resource_type", "auto",
                    "folder", "chat_media",
                    "overwrite", true,
                    "invalidate", true,
                    "use_filename", false
            );
            
            // Try multiple upload approaches to handle timestamp issues
            Map uploadResult;
            try {
                // First attempt with fresh Cloudinary instance
                uploadResult = freshCloudinary.uploader().upload(file.getBytes(), uploadParams);
            } catch (Exception timestampError) {
                if (timestampError.getMessage() != null && timestampError.getMessage().contains("Stale request")) {
                    log.warn("Timestamp error detected, attempting workaround...");
                    // Remove parameters that might cause timestamp issues and retry
                    Map simpleParams = ObjectUtils.asMap(
                            "resource_type", "auto",
                            "overwrite", true
                    );
                    uploadResult = freshCloudinary.uploader().upload(file.getBytes(), simpleParams);
                } else {
                    throw timestampError;
                }
            }

            // Create MediaFile entity
            MediaFile mediaFile = new MediaFile();
            mediaFile.setOriginalFilename(originalFilename);
            mediaFile.setStoredFilename(storedFilename);
            mediaFile.setFileUrl(uploadResult.get("secure_url").toString());
            mediaFile.setFileType(file.getContentType());
            mediaFile.setFileSize(file.getSize());
            mediaFile.setMimeType(file.getContentType());
            mediaFile.setUploadedBy(user);
            mediaFile.setCloudinaryPublicId(uploadResult.get("public_id").toString());

            // Set dimensions for images
            if (uploadResult.containsKey("width") && uploadResult.containsKey("height")) {
                mediaFile.setWidth((Integer) uploadResult.get("width"));
                mediaFile.setHeight((Integer) uploadResult.get("height"));
            }

            // Set duration for videos
            if (uploadResult.containsKey("duration")) {
                Double duration = (Double) uploadResult.get("duration");
                mediaFile.setDuration(duration.longValue());
            }

            // Generate thumbnail for videos
            if (isVideo(file.getContentType())) {
                String thumbnailUrl = generateVideoThumbnail(uploadResult.get("public_id").toString());
                mediaFile.setThumbnailUrl(thumbnailUrl);
            } else if (isImage(file.getContentType())) {
                // For images, create a smaller thumbnail
                String thumbnailUrl = cloudinary.url()
                        .transformation(new com.cloudinary.Transformation()
                                .width(200).height(200).crop("fill"))
                        .generate(uploadResult.get("public_id").toString());
                mediaFile.setThumbnailUrl(thumbnailUrl);
            }

            MediaFile savedFile = mediaFileRepository.save(mediaFile);

            log.info("Media file uploaded successfully with ID: {}", savedFile.getId());
            return convertToMediaUploadDTO(savedFile);

        } catch (Exception e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            
            // Check if it's a stale timestamp error and retry once
            if (e.getMessage() != null && e.getMessage().contains("Stale request")) {
                log.warn("Stale request detected, retrying with fresh Cloudinary instance...");
                try {
                    // Create completely fresh Cloudinary instance for retry
                    Cloudinary retryCloudinary = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", cloudinary.config.cloudName,
                            "api_key", cloudinary.config.apiKey,
                            "api_secret", cloudinary.config.apiSecret,
                            "secure", true
                    ));
                    
                    // Retry with absolutely minimal parameters to avoid timestamp validation
                    Map retryResult = retryCloudinary.uploader().upload(file.getBytes(),
                            ObjectUtils.asMap(
                                    "resource_type", "auto"
                            ));
                    
                    // Process successful retry result
                    MediaFile mediaFile = new MediaFile();
                    mediaFile.setOriginalFilename(originalFilename);
                    mediaFile.setStoredFilename(storedFilename);
                    mediaFile.setFileUrl(retryResult.get("secure_url").toString());
                    mediaFile.setFileType(file.getContentType());
                    mediaFile.setFileSize(file.getSize());
                    mediaFile.setMimeType(file.getContentType());
                    mediaFile.setUploadedBy(user);
                    mediaFile.setCloudinaryPublicId(retryResult.get("public_id").toString());
                    
                    // Set dimensions for images
                    if (retryResult.containsKey("width") && retryResult.containsKey("height")) {
                        mediaFile.setWidth((Integer) retryResult.get("width"));
                        mediaFile.setHeight((Integer) retryResult.get("height"));
                    }

                    // Set duration for videos
                    if (retryResult.containsKey("duration")) {
                        Double duration = (Double) retryResult.get("duration");
                        mediaFile.setDuration(duration.longValue());
                    }

                    // Generate thumbnail for videos
                    if (isVideo(file.getContentType())) {
                        String thumbnailUrl = generateVideoThumbnail(retryResult.get("public_id").toString());
                        mediaFile.setThumbnailUrl(thumbnailUrl);
                    } else if (isImage(file.getContentType())) {
                        // For images, create a smaller thumbnail
                        String thumbnailUrl = cloudinary.url()
                                .transformation(new com.cloudinary.Transformation()
                                        .width(200).height(200).crop("fill"))
                                .generate(retryResult.get("public_id").toString());
                        mediaFile.setThumbnailUrl(thumbnailUrl);
                    }

                    MediaFile savedFile = mediaFileRepository.save(mediaFile);
                    log.info("Media file uploaded successfully on retry with ID: {}", savedFile.getId());
                    return convertToMediaUploadDTO(savedFile);
                    
                } catch (Exception retryException) {
                    log.error("Retry also failed", retryException);
                    throw new RuntimeException("Failed to upload file after retry: " + retryException.getMessage());
                }
            }
            
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Get media file by ID
     */
    @Transactional(readOnly = true)
    public MediaFile getMediaFile(Long fileId, Long userId) {
        MediaFile mediaFile = mediaFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Media file not found"));

        // Check if user has access (for now, allow all - can be enhanced with chat participant check)
        if (!mediaFile.getUploadedBy().getId().equals(userId)) {
            // Could add additional security checks here
            log.warn("User {} trying to access media file {} owned by {}",
                    userId, fileId, mediaFile.getUploadedBy().getId());
        }

        return mediaFile;
    }

    /**
     * Download media file as Resource
     */
    @Transactional(readOnly = true)
    public Resource downloadMediaFile(Long fileId, Long userId) {
        MediaFile mediaFile = getMediaFile(fileId, userId);

        try {
            Path filePath = Paths.get(mediaFile.getFileUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + mediaFile.getOriginalFilename());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + mediaFile.getOriginalFilename());
        }
    }

    /**
     * Delete media file
     */
    public void deleteMediaFile(Long fileId, Long userId) {
        MediaFile mediaFile = mediaFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Media file not found"));

        // Only file owner can delete
        if (!mediaFile.getUploadedBy().getId().equals(userId)) {
            throw new RuntimeException("You don't have permission to delete this file");
        }

        try {
            // Delete from Cloudinary
            if (mediaFile.getCloudinaryPublicId() != null) {
                cloudinary.uploader().destroy(mediaFile.getCloudinaryPublicId(), ObjectUtils.emptyMap());
            }

            // Soft delete in database
            mediaFile.softDelete();
            mediaFileRepository.save(mediaFile);

            log.info("Media file deleted: {}", fileId);
        } catch (Exception e) {
            log.error("Error deleting file from Cloudinary", e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage());
        }
    }

    /**
     * Get user's uploaded files with pagination
     */
    @Transactional(readOnly = true)
    public Page<MediaUploadDTO> getUserMediaFiles(Long userId, Pageable pageable) {
        Page<MediaFile> mediaFiles = mediaFileRepository.findByUploadedByIdAndNotDeleted(userId, pageable);
        return mediaFiles.map(this::convertToMediaUploadDTO);
    }

    /**
     * Get user's files by type
     */
    @Transactional(readOnly = true)
    public Page<MediaUploadDTO> getUserMediaFilesByType(Long userId, String fileType, Pageable pageable) {
        Page<MediaFile> mediaFiles = mediaFileRepository.findByUploadedByIdAndFileType(userId, fileType, pageable);
        return mediaFiles.map(this::convertToMediaUploadDTO);
    }

    /**
     * Get user's storage statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStorageStats(Long userId) {
        Long totalFiles = mediaFileRepository.countByUploadedByIdAndNotDeleted(userId);
        Long totalSize = mediaFileRepository.calculateTotalFileSizeForUser(userId);

        return Map.of(
                "totalFiles", totalFiles,
                "totalSizeBytes", totalSize,
                "totalSizeFormatted", formatFileSize(totalSize),
                "maxAllowedSize", maxFileSize,
                "maxAllowedSizeFormatted", formatFileSize(maxFileSize)
        );
    }

    // Helper methods
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("File size exceeds maximum allowed size of " + formatFileSize(maxFileSize));
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedFileType(contentType)) {
            throw new RuntimeException("File type not allowed: " + contentType);
        }
    }

    private boolean isAllowedFileType(String contentType) {
        if (allowedTypes == null || allowedTypes.equals("*")) {
            return true; // Allow all types
        }

        return IMAGE_TYPES.contains(contentType) ||
                VIDEO_TYPES.contains(contentType) ||
                AUDIO_TYPES.contains(contentType) ||
                contentType.startsWith("application/"); // Documents
    }

    private boolean isImage(String contentType) {
        return contentType != null && IMAGE_TYPES.contains(contentType);
    }

    private boolean isVideo(String contentType) {
        return contentType != null && VIDEO_TYPES.contains(contentType);
    }

    private boolean isAudio(String contentType) {
        return contentType != null && AUDIO_TYPES.contains(contentType);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String generateVideoThumbnail(String publicId) {
        try {
            return cloudinary.url()
                    .transformation(new com.cloudinary.Transformation()
                            .width(300).height(200).crop("fill")
                            .videoSampling("1"))
                    .resourceType("video")
                    .format("jpg")
                    .generate(publicId);
        } catch (Exception e) {
            log.warn("Could not generate video thumbnail for: " + publicId, e);
            return null;
        }
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";

        int unit = 1024;
        if (bytes < unit) return bytes + " B";

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private MediaUploadDTO convertToMediaUploadDTO(MediaFile mediaFile) {
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
        return dto;
    }
}