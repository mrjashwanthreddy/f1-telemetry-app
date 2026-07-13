package com.f1telemetry.config;

import com.f1telemetry.repository.TelemetryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseCleanupRunner implements CommandLineRunner {

    private final TelemetryRecordRepository telemetryRecordRepository;

    @Override
    public void run(String... args) throws Exception {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            log.info("Starting database cleanup of empty/invalid telemetry records...");
            try {
                int deletedCount = telemetryRecordRepository.deleteEmptyOrInvalidRecords();
                log.info("Database cleanup completed successfully. Cleaned up {} empty/invalid telemetry records.", deletedCount);
            } catch (Exception e) {
                log.error("Failed to run database cleanup on startup", e);
            }
        });
    }
}
