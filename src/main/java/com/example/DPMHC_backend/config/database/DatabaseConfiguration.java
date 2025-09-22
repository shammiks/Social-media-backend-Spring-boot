package com.example.DPMHC_backend.config.database;

import com.example.DPMHC_backend.config.database.health.DatabaseHealthMonitor;
import com.example.DPMHC_backend.config.database.DatabaseProperties;
import com.example.DPMHC_backend.config.database.routing.RoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Main database configuration class for master-slave setup
 */
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
@RequiredArgsConstructor
@Slf4j
public class DatabaseConfiguration {
    
    private final DatabaseProperties databaseProperties;
    private final DatabaseHealthMonitor healthMonitor;
    
    /**
     * Create master database connection pool
     */
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        log.info("Creating master database connection pool");
        
        HikariConfig config = new HikariConfig();
        DatabaseProperties.Master master = databaseProperties.getMaster();
        
        config.setJdbcUrl(master.getUrl());
        config.setUsername(master.getUsername());
        config.setPassword(master.getPassword());
        config.setDriverClassName(master.getDriverClassName());
        
        // Connection pool settings
        config.setMaximumPoolSize(master.getMaxPoolSize());
        config.setMinimumIdle(master.getMinPoolSize());
        config.setConnectionTimeout(master.getConnectionTimeoutMs());
        config.setIdleTimeout(master.getIdleTimeoutMs());
        config.setMaxLifetime(master.getMaxLifetimeMs());
        
        // Performance settings
        config.setAutoCommit(databaseProperties.getConnectionPool().isAutoCommit());
        config.setLeakDetectionThreshold(databaseProperties.getConnectionPool().getLeakDetectionThresholdMs());
        config.setConnectionTestQuery(databaseProperties.getConnectionPool().getConnectionTestQuery());
        
        // Cache settings
        config.addDataSourceProperty("cachePrepStmts", databaseProperties.getConnectionPool().isCacheStatements());
        config.addDataSourceProperty("prepStmtCacheSize", databaseProperties.getConnectionPool().getPreparedStatementCacheSize());
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // MySQL specific settings for proper transaction handling
        config.addDataSourceProperty("useInformationSchema", "true");
        config.addDataSourceProperty("nullCatalogMeansCurrent", "true");
        config.addDataSourceProperty("nullNamePatternMatchesAll", "true");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("autoReconnect", "true");
        config.addDataSourceProperty("failOverReadOnly", "false");
        
        config.setPoolName("MasterCP");
        
        return new HikariDataSource(config);
    }
    
    /**
     * Create slave database connection pools
     */
    @Bean(name = "slaveDataSources")
    public Map<DatabaseType, DataSource> slaveDataSources() {
        log.info("Creating slave database connection pools");
        
        Map<DatabaseType, DataSource> dataSources = new HashMap<>();
        
        if (databaseProperties.getSlaves() != null) {
            for (int i = 0; i < databaseProperties.getSlaves().size() && i < 2; i++) {
                DatabaseProperties.Slave slave = databaseProperties.getSlaves().get(i);
                
                if (slave.isEnabled()) {
                    HikariConfig config = new HikariConfig();
                    
                    config.setJdbcUrl(slave.getUrl());
                    config.setUsername(slave.getUsername());
                    config.setPassword(slave.getPassword());
                    config.setDriverClassName(slave.getDriverClassName());
                    
                    // Connection pool settings
                    config.setMaximumPoolSize(slave.getMaxPoolSize());
                    config.setMinimumIdle(slave.getMinPoolSize());
                    config.setConnectionTimeout(slave.getConnectionTimeoutMs());
                    config.setIdleTimeout(slave.getIdleTimeoutMs());
                    config.setMaxLifetime(slave.getMaxLifetimeMs());
                    
                    // Performance settings for read-optimized slaves
                    config.setAutoCommit(databaseProperties.getConnectionPool().isAutoCommit());
                    config.setLeakDetectionThreshold(databaseProperties.getConnectionPool().getLeakDetectionThresholdMs());
                    config.setConnectionTestQuery(databaseProperties.getConnectionPool().getConnectionTestQuery());
                    config.setReadOnly(true); // Mark slave connections as read-only
                    
                    // Cache settings optimized for reads
                    config.addDataSourceProperty("cachePrepStmts", "true");
                    config.addDataSourceProperty("prepStmtCacheSize", "500"); // Larger cache for slaves
                    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    config.addDataSourceProperty("useServerPrepStmts", "true");
                    config.addDataSourceProperty("useLocalSessionState", "true");
                    config.addDataSourceProperty("cacheResultSetMetadata", "true");
                    config.addDataSourceProperty("cacheServerConfiguration", "true");
                    config.addDataSourceProperty("elideSetAutoCommits", "true");
                    config.addDataSourceProperty("maintainTimeStats", "false");
                    
                    // MySQL specific settings for slave connections
                    config.addDataSourceProperty("useInformationSchema", "true");
                    config.addDataSourceProperty("nullCatalogMeansCurrent", "true");
                    config.addDataSourceProperty("nullNamePatternMatchesAll", "true");
                    config.addDataSourceProperty("useUnicode", "true");
                    config.addDataSourceProperty("characterEncoding", "UTF-8");
                    config.addDataSourceProperty("autoReconnect", "true");
                    config.addDataSourceProperty("failOverReadOnly", "false");
                    
                    DatabaseType dbType = DatabaseType.getSlaveType(i);
                    config.setPoolName(slave.getName() != null ? slave.getName() : dbType.name() + "CP");
                    
                    dataSources.put(dbType, new HikariDataSource(config));
                    log.info("Created slave connection pool: {}", dbType);
                }
            }
        }
        
        return dataSources;
    }
    
    /**
     * Create routing data source that switches between master and slaves
     */
    @Bean(name = "routingDataSource")
    @Primary
    public DataSource routingDataSource() {
        log.info("Creating routing data source");
        
        RoutingDataSource routingDataSource = new RoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        
        // Add master data source
        DataSource masterDS = masterDataSource();
        targetDataSources.put(DatabaseType.MASTER, masterDS);
        
        // Add slave data sources
        Map<DatabaseType, DataSource> slaveDSMap = slaveDataSources();
        targetDataSources.putAll(slaveDSMap);
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDS); // Default to master
        
        // Initialize health monitoring with all datasources
        initializeHealthMonitoring(masterDS, slaveDSMap);
        
        return routingDataSource;
    }
    
    /**
     * Initialize health monitoring for all databases
     */
    private void initializeHealthMonitoring(DataSource masterDS, Map<DatabaseType, DataSource> slaveDSMap) {
        log.info("Initializing health monitoring for all databases");
        
        Map<DatabaseType, DataSource> allDataSources = new HashMap<>();
        allDataSources.put(DatabaseType.MASTER, masterDS);
        allDataSources.putAll(slaveDSMap);
        
        healthMonitor.initializeHealthMonitoring(allDataSources);
        log.info("Health monitoring initialized for {} databases", allDataSources.size());
    }
    
    /**
     * Entity Manager Factory configuration
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        log.info("Creating entity manager factory");
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(routingDataSource());
        em.setPackagesToScan("com.example.DPMHC_backend.model");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.format_sql", "true");
        
        // Performance optimizations
        properties.setProperty("hibernate.jdbc.batch_size", "25");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        
        // Transaction management for routing datasource
        properties.setProperty("hibernate.connection.autocommit", "false");
        properties.setProperty("hibernate.current_session_context_class", "org.springframework.orm.hibernate5.SpringSessionContext");
        
        // Cache settings
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");
        
        em.setJpaProperties(properties);
        
        return em;
    }
    
    /**
     * Transaction Manager configuration
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        log.info("Creating transaction manager");
        
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        
        return transactionManager;
    }
}