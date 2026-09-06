package com.f1telemetry.migration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseFreshInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void resetLegacyTablesIfPresent() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if legacy 'users' table exists in PostgreSQL
            boolean legacyUsersExists = false;
            try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public.users')")) {
                if (rs.next()) {
                    String reg = rs.getString(1);
                    legacyUsersExists = (reg != null && !reg.isBlank());
                }
            }

            if (legacyUsersExists) {
                log.info("Legacy 'users' table detected! Performing fresh table recreation for sim_drivers and race_engineers...");

                stmt.execute("DROP TABLE IF EXISTS user_preferences CASCADE");
                stmt.execute("DROP TABLE IF EXISTS lap_time_records CASCADE");
                stmt.execute("DROP TABLE IF EXISTS race_sessions CASCADE");
                stmt.execute("DROP TABLE IF EXISTS ai_usage_records CASCADE");
                stmt.execute("DROP TABLE IF EXISTS telemetry_records CASCADE");
                stmt.execute("DROP TABLE IF EXISTS users CASCADE");
                stmt.execute("DROP TABLE IF EXISTS sim_drivers CASCADE");
                stmt.execute("DROP TABLE IF EXISTS race_engineers CASCADE");

                log.info("Legacy tables successfully dropped. Fresh schema will now be initialized.");
            } else {
                log.info("Database schema is up to date (fresh sim_drivers and race_engineers architecture active).");
            }

        } catch (Exception e) {
            log.error("Database initialization check error: {}", e.getMessage(), e);
        }
    }
}
