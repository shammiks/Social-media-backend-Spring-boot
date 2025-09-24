package com.example.DPMHC_backend.model;

public enum ReportReason {
    SPAM("Spam"),
    HARASSMENT("Harassment or bullying"),
    INAPPROPRIATE_CONTENT("Inappropriate content"),
    MISINFORMATION("False information"),
    HATE_SPEECH("Hate speech"),
    VIOLENCE("Violence or threats"),
    COPYRIGHT("Copyright violation"),
    ADULT_CONTENT("Adult content"),
    SELF_HARM("Self-harm or suicide"),
    OTHER("Other");

    private final String displayName;

    ReportReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}