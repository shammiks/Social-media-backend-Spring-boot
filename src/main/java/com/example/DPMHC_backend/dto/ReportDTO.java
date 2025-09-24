package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.ReportReason;
import com.example.DPMHC_backend.model.ReportStatus;

import java.time.LocalDateTime;

public class ReportDTO {
    private Long id;
    private Long reportedPostId;
    private String reportedPostContent;
    private String reportedPostAuthor;
    private Long reporterId;
    private String reporterUsername;
    private ReportReason reason;
    private String description;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolvedByUsername;
    private String adminNotes;

    // Constructors
    public ReportDTO() {}

    public ReportDTO(Long id, Long reportedPostId, String reportedPostContent, String reportedPostAuthor,
                     Long reporterId, String reporterUsername, ReportReason reason, String description,
                     ReportStatus status, LocalDateTime createdAt, LocalDateTime resolvedAt,
                     String resolvedByUsername, String adminNotes) {
        this.id = id;
        this.reportedPostId = reportedPostId;
        this.reportedPostContent = reportedPostContent;
        this.reportedPostAuthor = reportedPostAuthor;
        this.reporterId = reporterId;
        this.reporterUsername = reporterUsername;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.resolvedByUsername = resolvedByUsername;
        this.adminNotes = adminNotes;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReportedPostId() {
        return reportedPostId;
    }

    public void setReportedPostId(Long reportedPostId) {
        this.reportedPostId = reportedPostId;
    }

    public String getReportedPostContent() {
        return reportedPostContent;
    }

    public void setReportedPostContent(String reportedPostContent) {
        this.reportedPostContent = reportedPostContent;
    }

    public String getReportedPostAuthor() {
        return reportedPostAuthor;
    }

    public void setReportedPostAuthor(String reportedPostAuthor) {
        this.reportedPostAuthor = reportedPostAuthor;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public void setReporterId(Long reporterId) {
        this.reporterId = reporterId;
    }

    public String getReporterUsername() {
        return reporterUsername;
    }

    public void setReporterUsername(String reporterUsername) {
        this.reporterUsername = reporterUsername;
    }

    public ReportReason getReason() {
        return reason;
    }

    public void setReason(ReportReason reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedByUsername() {
        return resolvedByUsername;
    }

    public void setResolvedByUsername(String resolvedByUsername) {
        this.resolvedByUsername = resolvedByUsername;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}