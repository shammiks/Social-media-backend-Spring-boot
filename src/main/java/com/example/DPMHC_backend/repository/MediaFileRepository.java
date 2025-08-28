package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.MediaFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    // Find media files uploaded by user
    @Query("SELECT m FROM MediaFile m " +
            "WHERE m.uploadedBy.id = :userId AND m.isDeleted = false " +
            "ORDER BY m.uploadedAt DESC")
    Page<MediaFile> findByUploadedByIdAndNotDeleted(@Param("userId") Long userId, Pageable pageable);

    // Find media files by type
    @Query("SELECT m FROM MediaFile m " +
            "WHERE m.uploadedBy.id = :userId AND m.isDeleted = false " +
            "AND m.fileType LIKE :fileType% " +
            "ORDER BY m.uploadedAt DESC")
    Page<MediaFile> findByUploadedByIdAndFileType(@Param("userId") Long userId,
                                                  @Param("fileType") String fileType,
                                                  Pageable pageable);

    // Find by Cloudinary public ID
    @Query("SELECT m FROM MediaFile m " +
            "WHERE m.cloudinaryPublicId = :publicId AND m.isDeleted = false")
    Optional<MediaFile> findByCloudinaryPublicId(@Param("publicId") String publicId);

    // Count user's uploaded files
    @Query("SELECT COUNT(m) FROM MediaFile m " +
            "WHERE m.uploadedBy.id = :userId AND m.isDeleted = false")
    Long countByUploadedByIdAndNotDeleted(@Param("userId") Long userId);

    // Calculate total file size for user
    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM MediaFile m " +
            "WHERE m.uploadedBy.id = :userId AND m.isDeleted = false")
    Long calculateTotalFileSizeForUser(@Param("userId") Long userId);
}
