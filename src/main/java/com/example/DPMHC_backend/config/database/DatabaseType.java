package com.example.DPMHC_backend.config.database;

/**
 * Enum to represent different database types in master-slave configuration
 */
public enum DatabaseType {
    MASTER,
    SLAVE_1,
    SLAVE_2;
    
    /**
     * Get database type based on routing decision
     */
    public static DatabaseType getSlaveType(int slaveIndex) {
        return switch (slaveIndex) {
            case 0 -> SLAVE_1;
            case 1 -> SLAVE_2;
            default -> SLAVE_1; // Default fallback
        };
    }
    
    /**
     * Check if this is a slave database
     */
    public boolean isSlave() {
        return this == SLAVE_1 || this == SLAVE_2;
    }
    
    /**
     * Check if this is master database
     */
    public boolean isMaster() {
        return this == MASTER;
    }
}