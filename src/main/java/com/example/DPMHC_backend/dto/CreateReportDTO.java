package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.ReportReason;

public class CreateReportDTO {
    private Long postId;
    private ReportReason reason;
    private String description;

    // Constructors
    public CreateReportDTO() {}

    public CreateReportDTO(Long postId, ReportReason reason, String description) {
        this.postId = postId;
        this.reason = reason;
        this.description = description;
    }

    // Getters and Setters
    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
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
}