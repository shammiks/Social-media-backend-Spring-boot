package com.example.DPMHC_backend.config.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;

@Configuration
@Profile("prod")
public class DatabaseUrlProcessor {

    @Autowired
    private Environment environment;

    @PostConstruct
    public void processPostgreSQLUrl() {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl != null && databaseUrl.startsWith("postgresql://")) {
            // Convert postgresql:// to jdbc:postgresql://
            String jdbcUrl = "jdbc:" + databaseUrl;
            System.setProperty("spring.datasource.url", jdbcUrl);
            System.out.println("Converted DATABASE_URL: " + databaseUrl + " -> " + jdbcUrl);
        }
    }
}