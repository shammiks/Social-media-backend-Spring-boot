package com.example.DPMHC_backend.model;

public enum NotificationType {
    // Social Media Events
    LIKE("liked your post"),
    COMMENT("commented on your post"),
    REPLY("replied to your comment"),
    FOLLOW("started following you"),
    UNFOLLOW("unfollowed you"),
    MENTION("mentioned you in a post"),
    TAG("tagged you in a post"),

    // Content Events
    POST_SHARED("shared your post"),
    POST_SAVED("saved your post"),

    // System Events
    WELCOME("Welcome to the platform!"),
    ACCOUNT_VERIFIED("Your account has been verified"),
    PASSWORD_CHANGED("Your password was changed"),
    LOGIN_ALERT("New login detected"),

    // Admin Events
    POST_APPROVED("Your post was approved"),
    POST_REJECTED("Your post was rejected"),
    ACCOUNT_WARNING("Account warning"),
    ACCOUNT_SUSPENDED("Account suspended"),

    // Group/Community Events
    GROUP_INVITE("invited you to join a group"),
    GROUP_REQUEST("requested to join your group"),
    GROUP_ACCEPTED("accepted your group request"),

    // Achievement Events
    MILESTONE("You reached a milestone!"),
    BADGE_EARNED("You earned a new badge!");

    private final String defaultMessage;

    NotificationType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
