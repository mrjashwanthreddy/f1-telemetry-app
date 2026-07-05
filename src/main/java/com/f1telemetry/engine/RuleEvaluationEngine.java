package com.f1telemetry.engine;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.state.CarState;
import com.f1telemetry.state.LiveSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluationEngine {

    private final RaceSessionRepository sessionRepository;
    private final LapTimeRecordRepository lapRepository;
    private final com.f1telemetry.service.ActiveUserService activeUserService;

    private long lastTireAlertTime = 0;
    private short lastFrontLeftWingDamage = 0;
    
    // Lap Tracking
    private short lastSeenLap = -1;
    private int cachedS1 = 0;
    private int cachedS2 = 0;
    private String currentSessionId = null;
    
    public List<AlertEvent> evaluate(LiveSessionState state, UserPreference prefs) {
        List<AlertEvent> alerts = new ArrayList<>();
        int playerIdx = state.getPlayerCarIndex();
        CarState playerCar = state.getCars()[playerIdx];
        long now = System.currentTimeMillis();

        // 1. Lap Completion Tracking
        trackLaps(playerCar);

        // 2. Rules Evaluation
        if (prefs == null) return alerts;

        if (now - lastTireAlertTime > 5000) {
            boolean overheating = false;
            short[] temps = playerCar.getTyreSurfaceTemps();
            for (int i = 0; i < 4; i++) {
                if (temps[i] >= prefs.getTireOverheatTemp()) {
                    overheating = true;
                    break;
                }
            }
            if (overheating) {
                alerts.add(new AlertEvent("TIRE_OVERHEAT", "Tire surface temperature exceeded threshold!", "WARNING", now));
                lastTireAlertTime = now;
            }
        }

        if (playerCar.getFrontLeftWingDamage() > lastFrontLeftWingDamage) {
            alerts.add(new AlertEvent("DAMAGE", "Front left wing damage increased to " + playerCar.getFrontLeftWingDamage() + "%", "CRITICAL", now));
            lastFrontLeftWingDamage = playerCar.getFrontLeftWingDamage();
        } else if (playerCar.getFrontLeftWingDamage() < lastFrontLeftWingDamage) {
            lastFrontLeftWingDamage = playerCar.getFrontLeftWingDamage();
        }

        return alerts;
    }

    private void trackLaps(CarState playerCar) {
        if (lastSeenLap == -1) {
            lastSeenLap = playerCar.getCurrentLapNum();
            currentSessionId = java.util.UUID.randomUUID().toString(); // New session
            return;
        }

        // Cache sectors while in the lap
        if (playerCar.getSector1TimeInMS() > 0) cachedS1 = playerCar.getSector1TimeInMS();
        if (playerCar.getSector2TimeInMS() > 0) cachedS2 = playerCar.getSector2TimeInMS();

        // If lap increments, save the previous lap
        if (playerCar.getCurrentLapNum() > lastSeenLap && playerCar.getLastLapTimeInMS() > 0) {
            log.info("Lap {} completed. Saving to database.", lastSeenLap);
            saveLapRecord(lastSeenLap, cachedS1, cachedS2, (int) playerCar.getLastLapTimeInMS());
            
            lastSeenLap = playerCar.getCurrentLapNum();
            cachedS1 = 0;
            cachedS2 = 0;
        }
    }

    private void saveLapRecord(short lapNum, int s1, int s2, int totalLapTime) {
        com.f1telemetry.domain.User activeUser = activeUserService.getActiveUser();
        if (activeUser == null) {
            log.warn("No active player registered. Cannot save lap data.");
            return;
        }

        RaceSession session = sessionRepository.findBySessionId(currentSessionId)
                .orElseGet(() -> sessionRepository.save(new RaceSession(activeUser, currentSessionId, "Spa-Francorchamps", System.currentTimeMillis())));

        int s3 = totalLapTime - s1 - s2;
        if (s3 < 0) s3 = 0; // Sanity check if data is incomplete
        
        LapTimeRecord record = new LapTimeRecord(session, lapNum, s1, s2, s3, totalLapTime);
        lapRepository.save(record);
    }
}
