package com.example.DPMHC_backend.model;

public enum NotificationPriority {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    URGENT(4);

    private final int level;

    NotificationPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
