package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Report;
import com.example.DPMHC_backend.model.ReportStatus;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    
    // Find reports by status
    List<Report> findByStatus(ReportStatus status);
    
    // Find reports for a specific post
    List<Report> findByReportedPost(Post post);
    
    // Find reports by a specific reporter
    List<Report> findByReporter(User reporter);
    
    // Check if a user has already reported a specific post
    Optional<Report> findByReporterAndReportedPost(User reporter, Post post);
    
    // Get all pending reports for admin review (paginated)
    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
    
    // Count pending reports
    long countByStatus(ReportStatus status);
    
    // Get reports resolved by a specific admin
    List<Report> findByResolvedBy(User admin);
    
    // Find reports by post ID
    @Query("SELECT r FROM Report r WHERE r.reportedPost.id = :postId")
    List<Report> findByPostId(@Param("postId") Long postId);
    
    // Check if post has any pending reports
    @Query("SELECT COUNT(r) > 0 FROM Report r WHERE r.reportedPost.id = :postId AND r.status = :status")
    boolean existsByPostIdAndStatus(@Param("postId") Long postId, @Param("status") ReportStatus status);
}