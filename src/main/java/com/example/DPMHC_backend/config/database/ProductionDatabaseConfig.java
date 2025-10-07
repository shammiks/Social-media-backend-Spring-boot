package com.example.DPMHC_backend.config.database;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Simple database configuration for production deployment
 * This replaces the complex master-slave setup with a single database
 */
@Configuration
@Profile("prod")
public class ProductionDatabaseConfig {

    /**
     * Create a simple datasource for production using environment variables
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        return DataSourceBuilder
            .create()
            .url(System.getenv("DATABASE_URL"))
            .username(System.getenv("DB_USERNAME"))
            .password(System.getenv("DB_PASSWORD"))
            .driverClassName("org.postgresql.Driver")
            .build();
    }
}