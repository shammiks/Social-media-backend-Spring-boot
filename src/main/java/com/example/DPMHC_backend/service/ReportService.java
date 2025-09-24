package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.dto.CreateReportDTO;
import com.example.DPMHC_backend.dto.ReportDTO;
import com.example.DPMHC_backend.model.*;
import com.example.DPMHC_backend.repository.PostRepository;
import com.example.DPMHC_backend.repository.ReportRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Create a new report for a post
     */
    public ReportDTO createReport(CreateReportDTO createReportDTO, Long reporterId) {
        // Find the reporter
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("Reporter not found"));

        // Find the post
        Post post = postRepository.findById(createReportDTO.getPostId())
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Create the report (allow multiple reports from same user)
        Report report = new Report(post, reporter, createReportDTO.getReason(), createReportDTO.getDescription());
        report = reportRepository.save(report);

        // Send notification to all admins
        sendReportNotificationToAdmins(report);

        return convertToDTO(report);
    }

    /**
     * Get all reports for admin review
     */
    public Page<ReportDTO> getAllReports(Pageable pageable) {
        Page<Report> reports = reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable);
        return reports.map(this::convertToDTO);
    }

    /**
     * Get report by ID
     */
    public ReportDTO getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        return convertToDTO(report);
    }

    /**
     * Update report status
     */
    public ReportDTO updateReportStatus(Long reportId, ReportStatus newStatus, Long adminId, String adminNotes) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        report.setStatus(newStatus);
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        report.setAdminNotes(adminNotes);

        report = reportRepository.save(report);
        return convertToDTO(report);
    }

    /**
     * Get reports for a specific post
     */
    public List<ReportDTO> getReportsForPost(Long postId) {
        List<Report> reports = reportRepository.findByPostId(postId);
        return reports.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * Get pending reports count
     */
    public long getPendingReportsCount() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    /**
     * Check if a post has pending reports
     */
    public boolean hasPostPendingReports(Long postId) {
        return reportRepository.existsByPostIdAndStatus(postId, ReportStatus.PENDING);
    }

    /**
     * Send notification to all admins about a new report
     */
    private void sendReportNotificationToAdmins(Report report) {
        List<User> admins = userRepository.findByIsAdminTrue();
        
        for (User admin : admins) {
            try {
                String message = String.format("New post report: %s reported a post by %s for %s", 
                    report.getReporter().getUsername(),
                    report.getReportedPost().getUser().getUsername(),
                    report.getReason().getDisplayName());
                
                notificationService.createNotification(
                    admin,
                    NotificationType.ADMIN_REPORT,
                    message,
                    report.getReportedPost().getId(),
                    report.getReporter().getId(),
                    NotificationPriority.HIGH
                );
            } catch (Exception e) {
                // Log error but don't fail the report creation
                System.err.println("Failed to send report notification to admin " + admin.getUsername() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Convert Report entity to DTO
     */
    private ReportDTO convertToDTO(Report report) {
        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        dto.setReportedPostId(report.getReportedPost().getId());
        dto.setReportedPostContent(report.getReportedPost().getContent());
        dto.setReportedPostAuthor(report.getReportedPost().getUser().getUsername());
        dto.setReporterId(report.getReporter().getId());
        dto.setReporterUsername(report.getReporter().getUsername());
        dto.setReason(report.getReason());
        dto.setDescription(report.getDescription());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setResolvedAt(report.getResolvedAt());
        dto.setResolvedByUsername(report.getResolvedBy() != null ? report.getResolvedBy().getUsername() : null);
        dto.setAdminNotes(report.getAdminNotes());
        return dto;
    }
}