package com.example.DPMHC_backend.model;

public enum ReportStatus {
    PENDING("Pending review"),
    UNDER_REVIEW("Under review"),
    RESOLVED("Resolved"),
    DISMISSED("Dismissed");

    private final String displayName;

    ReportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}