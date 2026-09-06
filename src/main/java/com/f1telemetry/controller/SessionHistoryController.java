package com.f1telemetry.controller;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceEngineer;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.domain.SimDriver;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceEngineerRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.repository.SimDriverRepository;
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
    private final SimDriverRepository simDriverRepository;
    private final RaceEngineerRepository raceEngineerRepository;
    private final com.f1telemetry.repository.TelemetryRecordRepository telemetryRecordRepository;

    private Optional<SimDriver> getDriverForSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
        if (driverOpt.isPresent()) {
            return driverOpt;
        }
        Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
        if (engineerOpt.isPresent() && engineerOpt.get().getAssignedDriverId() != null) {
            return simDriverRepository.findById(engineerOpt.get().getAssignedDriverId());
        }
        return Optional.empty();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<RaceSession>> getAllSessions() {
        return getDriverForSession()
                .map(driver -> {
                    List<RaceSession> sessions = sessionRepository.findByDriverOrderByTimestampDesc(driver);
                    log.debug("Fetched {} sessions for driver '{}'", sessions.size(), driver.getUsername());
                    return ResponseEntity.ok(sessions);
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/sessions/{sessionId}/laps")
    public ResponseEntity<List<LapTimeRecord>> getLapsForSession(@PathVariable String sessionId) {
        return getDriverForSession().flatMap(driver -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getDriver().getId().equals(driver.getId())))
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
        return getDriverForSession().flatMap(driver -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getDriver().getId().equals(driver.getId())))
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

    @GetMapping("/sessions/{sessionId}/export/csv")
    public ResponseEntity<String> exportSessionCsv(@PathVariable String sessionId) {
        return getDriverForSession().flatMap(driver -> sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getDriver().getId().equals(driver.getId())))
                .map(session -> {
                    List<LapTimeRecord> laps = lapRepository.findByRaceSessionOrderByLapNumberAsc(session);
                    StringBuilder csv = new StringBuilder();
                    csv.append("Lap,Sector1_ms,Sector2_ms,Sector3_ms,Total_ms,TyreWear_FL,TyreWear_FR,TyreWear_RL,TyreWear_RR,FuelRemaining_kg\n");
                    for (LapTimeRecord lap : laps) {
                        csv.append(lap.getLapNumber()).append(",")
                           .append(lap.getSector1TimeInMS()).append(",")
                           .append(lap.getSector2TimeInMS()).append(",")
                           .append(lap.getSector3TimeInMS()).append(",")
                           .append(lap.getTotalLapTimeInMS()).append(",")
                           .append(lap.getTyreWearFL()).append(",")
                           .append(lap.getTyreWearFR()).append(",")
                           .append(lap.getTyreWearRL()).append(",")
                           .append(lap.getTyreWearRR()).append(",")
                           .append(lap.getFuelRemainingKg()).append("\n");
                    }
                    return ResponseEntity.ok()
                            .header("Content-Disposition", "attachment; filename=\"session-" + sessionId + ".csv\"")
                            .header("Content-Type", "text/csv")
                            .body(csv.toString());
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
