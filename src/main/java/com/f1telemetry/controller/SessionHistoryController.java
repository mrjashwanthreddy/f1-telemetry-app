package com.f1telemetry.controller;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.domain.User;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class SessionHistoryController {

    private final RaceSessionRepository sessionRepository;
    private final LapTimeRecordRepository lapRepository;
    private final UserRepository userRepository;
    private final com.f1telemetry.repository.TelemetryRecordRepository telemetryRecordRepository;

    private Optional<User> getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<RaceSession>> getAllSessions() {
        return getAuthenticatedUser()
                .map(user -> ResponseEntity.ok(sessionRepository.findByUserOrderByTimestampDesc(user)))
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/sessions/{sessionId}/laps")
    public ResponseEntity<List<LapTimeRecord>> getLapsForSession(@PathVariable String sessionId) {
        return getAuthenticatedUser().flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getUser().getId().equals(user.getId())))
                .map(session -> ResponseEntity.ok(lapRepository.findByRaceSessionOrderByLapNumberAsc(session)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/laps/{lapNumber}/telemetry")
    public ResponseEntity<List<com.f1telemetry.domain.TelemetryRecord>> getTelemetryForLap(@PathVariable String sessionId, @PathVariable int lapNumber) {
        return getAuthenticatedUser().flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getUser().getId().equals(user.getId())))
                .map(session -> ResponseEntity.ok(telemetryRecordRepository.findBySessionIdAndCurrentLapNumOrderByTimestampAsc(sessionId, lapNumber)))
                .orElse(ResponseEntity.notFound().build());
    }
}
