package com.f1telemetry.controller;

import com.f1telemetry.domain.TelemetryRecord;
import com.f1telemetry.repository.TelemetryRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TelemetryRecordRepository telemetryRecordRepository;

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<List<TelemetryRecord>> getSessionTelemetry(@PathVariable String sessionId) {
        return ResponseEntity.ok(telemetryRecordRepository.findBySessionIdOrderByTimestampAsc(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/lap/{lapNum}")
    public ResponseEntity<List<TelemetryRecord>> getLapTelemetry(@PathVariable String sessionId, @PathVariable int lapNum) {
        return ResponseEntity.ok(telemetryRecordRepository.findBySessionIdAndCurrentLapNumOrderByTimestampAsc(sessionId, lapNum));
    }
}
