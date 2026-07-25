package com.f1telemetry.controller;

import com.f1telemetry.domain.TelemetryRecord;
import com.f1telemetry.repository.TelemetryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TelemetryRecordRepository telemetryRecordRepository;

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<TelemetryRecord>> getSessionTelemetry(@PathVariable String sessionId) {
        log.debug("Analytics query: full session telemetry for session '{}'", sessionId);
        List<TelemetryRecord> records = telemetryRecordRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        log.debug("Analytics query returned {} records for session '{}'", records.size(), sessionId);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/sessions/{sessionId}/lap/{lapNum}")
    public ResponseEntity<List<TelemetryRecord>> getLapTelemetry(@PathVariable String sessionId, @PathVariable int lapNum) {
        log.debug("Analytics query: lap telemetry for session '{}' lap {}", sessionId, lapNum);
        List<TelemetryRecord> records = telemetryRecordRepository.findBySessionIdAndCurrentLapNumOrderByTimestampAsc(sessionId, lapNum);
        log.debug("Analytics query returned {} records for session '{}' lap {}", records.size(), sessionId, lapNum);
        return ResponseEntity.ok(records);
    }
}
