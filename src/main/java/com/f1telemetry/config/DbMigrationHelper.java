package com.f1telemetry.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Validates and runs database migrations on startup.
 * Automatically adds columns to the database if they are not already present.
 */
@Slf4j
@Component
@Order(1) // Run before database cleanup runners
@RequiredArgsConstructor
public class DbMigrationHelper implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("[DbMigration] Running database schema validation for UDP configurations...");
        try {
            // PostgreSQL syntax to add columns dynamically if they do not exist
            jdbcTemplate.execute(
                    "ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS udp_host VARCHAR(255) DEFAULT '127.0.0.1'");
            jdbcTemplate
                    .execute("ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS udp_port INTEGER DEFAULT 20777");
            log.info("[DbMigration] Database schema validation completed successfully.");
        } catch (Exception e) {
            log.error("[DbMigration] Failed to run schema validation check: {}", e.getMessage());
        }

        // Clean up old backup files left behind by auto-updates
        try {
            File rootDir = new File(".").getAbsoluteFile();
            cleanOldFilesInDir(rootDir);
            File appDir = new File(rootDir, "app");
            if (appDir.exists() && appDir.isDirectory()) {
                cleanOldFilesInDir(appDir);
            }
        } catch (Exception e) {
            log.warn("[Update] Failed to clean up old backup files: {}", e.getMessage());
        }
    }

    private void cleanOldFilesInDir(File dir) {
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".old"));
        if (files == null)
            return;
        for (File file : files) {
            if (file.isFile()) {
                if (file.delete()) {
                    log.info("[Update] Cleaned up residual backup file: {}", file.getName());
                } else {
                    file.deleteOnExit(); // fallback
                }
            }
        }
    }
}
