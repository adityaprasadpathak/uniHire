package com.unihire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

/**
 * Configuration class responsible for initializing and cleaning up the database schema
 * for PgVector during application startup.
 * Specifically designed to run only in the 'local' profile.
 */
@Configuration
@Profile("local")
public class DatabaseCleanupConfig {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleanupConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes after the bean is constructed to recreate the vector_store table.
     * Drops the existing table, ensures the vector extension is available,
     * and creates a fresh table with 3072 dimensions tailored for the embedding model.
     */
    @PostConstruct
    public void cleanupVectorStoreTable() {
        try {
            // Drop old table to clean up the schema
            jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store CASCADE");
            System.out.println("DATABASE CLEANUP: Dropped vector_store table successfully.");

            // Create the PgVector extension if it doesn't exist
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

            // Create the table manually with 3072 dimensions and NO HNSW index
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS vector_store (" +
                "  id uuid NOT NULL PRIMARY KEY," +
                "  content text," +
                "  metadata jsonb," +
                "  embedding vector(3072)" +
                ")"
            );
            System.out.println("DATABASE CLEANUP: Created vector_store table manually with 3072 dimensions.");
        } catch (Exception e) {
            System.err.println("DATABASE CLEANUP: Failed during schema initialization: " + e.getMessage());
        }
    }
}
