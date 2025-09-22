package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.UserWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

    /**
     * Count the number of warnings for a specific user
     */
    long countByUser(User user);

    /**
     * Count warnings by user ID
     */
    @Query("SELECT COUNT(w) FROM UserWarning w WHERE w.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /**
     * Find all warnings for a specific user, ordered by creation date (newest first)
     */
    List<UserWarning> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Find all warnings for a specific user by user ID
     */
    @Query("SELECT w FROM UserWarning w WHERE w.user.id = :userId ORDER BY w.createdAt DESC")
    List<UserWarning> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Check if a user has any warnings
     */
    boolean existsByUser(User user);

    /**
     * Check if a user has any warnings by user ID
     */
    @Query("SELECT COUNT(w) > 0 FROM UserWarning w WHERE w.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);

    /**
     * Delete all warnings related to a specific post
     */
    @Modifying
    @Query("DELETE FROM UserWarning w WHERE w.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}