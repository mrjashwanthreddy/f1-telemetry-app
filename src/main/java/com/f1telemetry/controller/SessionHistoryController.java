package com.f1telemetry.controller;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.domain.User;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
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
                .map(user -> {
                    List<RaceSession> sessions = sessionRepository.findByUserOrderByTimestampDesc(user);
                    log.debug("Fetched {} sessions for user '{}'", sessions.size(), user.getUsername());
                    return ResponseEntity.ok(sessions);
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/sessions/{sessionId}/laps")
    public ResponseEntity<List<LapTimeRecord>> getLapsForSession(@PathVariable String sessionId) {
        return getAuthenticatedUser().flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getUser().getId().equals(user.getId())))
                .map(session -> {
                    List<LapTimeRecord> laps = lapRepository.findByRaceSessionOrderByLapNumberAsc(session);
                    log.debug("Fetched {} laps for session '{}'", laps.size(), sessionId);
                    return ResponseEntity.ok(laps);
                })
                .orElseGet(() -> {
                    log.debug("Session '{}' not found or access denied", sessionId);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/sessions/{sessionId}/laps/{lapNumber}/telemetry")
    public ResponseEntity<List<com.f1telemetry.domain.TelemetryRecord>> getTelemetryForLap(@PathVariable String sessionId, @PathVariable int lapNumber) {
        return getAuthenticatedUser().flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getUser().getId().equals(user.getId())))
                .map(session -> {
                    List<com.f1telemetry.domain.TelemetryRecord> records = telemetryRecordRepository.findBySessionIdAndCurrentLapNumOrderByTimestampAsc(sessionId, lapNumber);
                    log.debug("Fetched {} telemetry records for session '{}' lap {}", records.size(), sessionId, lapNumber);
                    return ResponseEntity.ok(records);
                })
                .orElseGet(() -> {
                    log.debug("Telemetry query denied — session '{}' not found or not owned", sessionId);
                    return ResponseEntity.notFound().build();
                });
    }

    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        return getAuthenticatedUser().flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getUser().getId().equals(user.getId())))
                .map(session -> {
                    log.info("Deleting session '{}' (track: {}, type: {})",
                            sessionId, session.getTrackName(), session.getSessionType());
                    // Delete telemetry records FIRST (bulk delete, avoids row-by-row Hibernate conflict)
                    telemetryRecordRepository.bulkDeleteBySessionId(sessionId);
                    // Delete laps
                    lapRepository.deleteByRaceSession(session);
                    // Delete the session itself
                    sessionRepository.delete(session);
                    log.info("Session '{}' deleted successfully", sessionId);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseGet(() -> {
                    log.warn("Delete request for session '{}' — not found or access denied", sessionId);
                    return ResponseEntity.notFound().build();
                });
    }
}
