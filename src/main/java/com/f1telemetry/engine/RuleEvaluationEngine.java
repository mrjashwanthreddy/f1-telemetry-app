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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluationEngine {

    private final RaceSessionRepository sessionRepository;
    private final LapTimeRecordRepository lapRepository;
    private final com.f1telemetry.service.ActiveUserService activeUserService;
    private final com.f1telemetry.repository.UserRepository userRepository;

    // ── F1 25 Track ID → Circuit Name ─────────────────────────────────────────
    // Source: F1 25 UDP Specification Appendix (trackId, int8)
    private static final Map<Integer, String> TRACK_NAMES = Map.ofEntries(
            Map.entry(0, "Melbourne"),
            Map.entry(1, "Paul Ricard"),
            Map.entry(2, "Shanghai"),
            Map.entry(3, "Sakhir (Bahrain)"),
            Map.entry(4, "Catalunya"),
            Map.entry(5, "Monaco"),
            Map.entry(6, "Montreal"),
            Map.entry(7, "Silverstone"),
            Map.entry(8, "Hockenheim"),
            Map.entry(9, "Hungaroring"),
            Map.entry(10, "Spa"),
            Map.entry(11, "Monza"),
            Map.entry(12, "Singapore"),
            Map.entry(13, "Suzuka"),
            Map.entry(14, "Abu Dhabi"),
            Map.entry(15, "Texas (COTA)"),
            Map.entry(16, "Brazil"),
            Map.entry(17, "Austria"),
            Map.entry(18, "Sochi"),
            Map.entry(19, "Mexico City"),
            Map.entry(20, "Baku (Azerbaijan)"),
            Map.entry(21, "Sakhir Short"),
            Map.entry(22, "Silverstone Short"),
            Map.entry(23, "Texas Short"),
            Map.entry(24, "Suzuka Short"),
            Map.entry(25, "Hanoi"),
            Map.entry(26, "Zandvoort"),
            Map.entry(27, "Imola"),
            Map.entry(28, "Portimao"),
            Map.entry(29, "Jeddah"),
            Map.entry(30, "Miami"),
            Map.entry(31, "Las Vegas"),
            Map.entry(32, "Losail (Qatar)"));

    // ── F1 25 Session Type → Label ────────────────────────────────────────────
    // Source: F1 25 UDP Specification Appendix (sessionType, uint8)
    private static final Map<Integer, String> SESSION_TYPES = Map.ofEntries(
            Map.entry(0, "Unknown"),
            Map.entry(1, "Practice 1"),
            Map.entry(2, "Practice 2"),
            Map.entry(3, "Practice 3"),
            Map.entry(4, "Short Practice"),
            Map.entry(5, "Qualifying 1"),
            Map.entry(6, "Qualifying 2"),
            Map.entry(7, "Qualifying 3"),
            Map.entry(8, "Short Qualifying"),
            Map.entry(9, "One-Shot Qualifying"),
            Map.entry(10, "Sprint Shootout 1"),
            Map.entry(11, "Sprint Shootout 2"),
            Map.entry(12, "Sprint Shootout 3"),
            Map.entry(13, "Short Sprint Shootout"),
            Map.entry(14, "One-Shot Sprint Shootout"),
            Map.entry(15, "Race"),
            Map.entry(16, "Race 2"),
            Map.entry(17, "Race 3"),
            Map.entry(18, "Time Trial"));

    private long lastTireAlertTime = 0;
    private short lastFrontLeftWingDamage = 0;

    // Session and Lap Tracking
    private String currentDbSessionId = null;
    private String currentGameSessionId = null;
    private short lastSeenLap = -1;
    private int cachedS1 = 0;
    private int cachedS2 = 0;
    private int currentLapOffset = 0;

    public List<AlertEvent> evaluate(LiveSessionState state, UserPreference prefs) {
        List<AlertEvent> alerts = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 1. Inactivity detection: if no packets for 10s, auto-end the current tracking session
        if (state.getLastUpdateTime() > 0 && (now - state.getLastUpdateTime() > 10000)) {
            if (currentDbSessionId != null) {
                log.info("Inactivity detected (10s). Resetting current session tracking.");
                endSession();
            }
            return alerts;
        }

        // 2. Prevent creating dummy sessions if there is no active user OR no packets have been received yet
        if (state.getSessionId() == null || activeUserService.getActiveUser() == null) {
            return alerts;
        }

        int playerIdx = state.getPlayerCarIndex();
        CarState playerCar = state.getCars()[playerIdx];

        // 3. Lap Completion Tracking
        trackLaps(playerCar, state);

        // 4. Rules Evaluation
        if (prefs == null)
            return alerts;

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
                alerts.add(new AlertEvent("TIRE_OVERHEAT", "Tire surface temperature exceeded threshold!", "WARNING",
                        now));
                lastTireAlertTime = now;
            }
        }

        if (playerCar.getFrontLeftWingDamage() > lastFrontLeftWingDamage) {
            alerts.add(new AlertEvent("DAMAGE",
                    "Front left wing damage increased to " + playerCar.getFrontLeftWingDamage() + "%", "CRITICAL",
                    now));
            lastFrontLeftWingDamage = playerCar.getFrontLeftWingDamage();
        } else if (playerCar.getFrontLeftWingDamage() < lastFrontLeftWingDamage) {
            lastFrontLeftWingDamage = playerCar.getFrontLeftWingDamage();
        }

        return alerts;
    }

    private RaceSession createOrGetSession(LiveSessionState state) {
        com.f1telemetry.domain.User rawUser = activeUserService.getActiveUser();
        if (rawUser == null) {
            rawUser = userRepository.findByUsername("jashwanth")
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
            if (rawUser == null) {
                log.warn("No active player registered and no users found in DB. Cannot save session.");
                return null;
            }
        }

        final com.f1telemetry.domain.User activeUser = rawUser;
        String dbSessionId = getDbSessionId(state);

        return sessionRepository.findBySessionId(dbSessionId).orElseGet(() -> {
            String trackName = resolveTrackName(state.getTrackId());
            String sessionType = resolveSessionType(state);
            log.info("Creating session record in DB: [{}] — {} ({})", dbSessionId, trackName, sessionType);
            RaceSession newSession = new RaceSession(activeUser, dbSessionId, trackName, sessionType, System.currentTimeMillis());
            return sessionRepository.save(newSession);
        });
    }

    private void trackLaps(CarState playerCar, LiveSessionState state) {
        String dbSessionId = getDbSessionId(state);
        String gameSessionId = state.getSessionId();

        if (lastSeenLap == -1) {
            lastSeenLap = playerCar.getCurrentLapNum();
            currentDbSessionId = dbSessionId;
            currentGameSessionId = gameSessionId;
            currentLapOffset = 0;
            state.setLapOffset(currentLapOffset);

            RaceSession session = createOrGetSession(state);
            if (session != null) {
                Integer maxLapNum = lapRepository.findMaxLapNumberByRaceSession(session);
                if (maxLapNum != null) {
                    currentLapOffset = maxLapNum;
                    state.setLapOffset(currentLapOffset);
                    log.info("Reused database session {}. Set lapOffset to {}.", dbSessionId, currentLapOffset);
                }
            }
            return;
        }

        // 1. Check if the database session ID changed (e.g. new weekend or starting a new TT session)
        if (dbSessionId != null && !dbSessionId.equals(currentDbSessionId)) {
            log.info("Database session changed from {} to {}. Starting new session.", currentDbSessionId, dbSessionId);
            currentDbSessionId = dbSessionId;
            currentGameSessionId = gameSessionId;
            lastSeenLap = playerCar.getCurrentLapNum();
            cachedS1 = 0;
            cachedS2 = 0;
            currentLapOffset = 0;
            state.setLapOffset(currentLapOffset);

            RaceSession session = createOrGetSession(state);
            if (session != null) {
                Integer maxLapNum = lapRepository.findMaxLapNumberByRaceSession(session);
                if (maxLapNum != null) {
                    currentLapOffset = maxLapNum;
                    state.setLapOffset(currentLapOffset);
                    log.info("Reused database session {}. Set lapOffset to {}.", dbSessionId, currentLapOffset);
                }
            }
            return;
        }

        // 2. Check if the game session ID changed within the SAME database session (e.g. Practice -> Qualifying in Career)
        if (gameSessionId != null && !gameSessionId.equals(currentGameSessionId)) {
            log.info("Game session changed from {} to {} within database session {}. Resetting lap tracking.", 
                     currentGameSessionId, gameSessionId, currentDbSessionId);
            currentGameSessionId = gameSessionId;
            lastSeenLap = playerCar.getCurrentLapNum();
            cachedS1 = 0;
            cachedS2 = 0;

            RaceSession session = createOrGetSession(state);
            if (session != null) {
                Integer maxLapNum = lapRepository.findMaxLapNumberByRaceSession(session);
                if (maxLapNum != null) {
                    currentLapOffset = maxLapNum;
                    state.setLapOffset(currentLapOffset);
                    log.info("Reused database session {}. Updated lapOffset to {}.", dbSessionId, currentLapOffset);
                }
            }
            return;
        }

        // Cache sectors while still in the lap
        if (playerCar.getSector1TimeInMS() > 0)
            cachedS1 = playerCar.getSector1TimeInMS();
        if (playerCar.getSector2TimeInMS() > 0)
            cachedS2 = playerCar.getSector2TimeInMS();

        // Lap incremented → previous lap is complete, persist it
        if (playerCar.getCurrentLapNum() > lastSeenLap && playerCar.getLastLapTimeInMS() > 0) {
            int completedLapDbNum = lastSeenLap + currentLapOffset;
            log.info("Lap {} (Game lap {}) completed in session {}. Saving to database as lap {}.", 
                     lastSeenLap, lastSeenLap, currentDbSessionId, completedLapDbNum);
            saveLapRecord((short) completedLapDbNum, cachedS1, cachedS2, (int) playerCar.getLastLapTimeInMS(), state);

            lastSeenLap = playerCar.getCurrentLapNum();
            cachedS1 = 0;
            cachedS2 = 0;
        }
    }

    private void saveLapRecord(short lapNum, int s1, int s2, int totalLapTime, LiveSessionState state) {
        RaceSession session = createOrGetSession(state);
        if (session == null) {
            log.warn("Cannot save lap details: active user not found.");
            return;
        }

        int s3 = totalLapTime - s1 - s2;
        if (s3 < 0)
            s3 = 0; // Sanity check for incomplete sector data

        LapTimeRecord record = new LapTimeRecord(session, lapNum, s1, s2, s3, totalLapTime);
        lapRepository.save(record);
    }

    public void endSession() {
        log.info("Session end triggered. Resetting tracking variables.");
        this.currentDbSessionId = null;
        this.currentGameSessionId = null;
        this.lastSeenLap = -1;
        this.currentLapOffset = 0;
        this.cachedS1 = 0;
        this.cachedS2 = 0;
    }

    // ── Public lookup helpers ─────────────────────────────────────────

    public static String getDbSessionId(LiveSessionState state) {
        if (state == null) return null;
        if (state.getWeekendLinkIdentifier() != 0) {
            return "weekend-" + state.getWeekendLinkIdentifier();
        }
        return state.getSessionId();
    }

    /**
     * Resolves the human-readable circuit name from the F1 25 trackId byte.
     * Returns a descriptive fallback if the ID is not in the lookup table.
     */
    public static String resolveTrackName(byte trackId) {
        return TRACK_NAMES.getOrDefault((int) trackId, "Unknown Track (ID " + trackId + ")");
    }

    /**
     * Resolves the session mode label from the F1 25 sessionType short.
     * Covers all modes: Practice, Qualifying, Race, Time Trial, Sprint, etc.
     */
    public static String resolveSessionType(LiveSessionState state) {
        if (state.getWeekendLinkIdentifier() != 0) {
            if (state.getGameMode() == 19 || state.getGameMode() == 20 || state.getGameMode() == 21 || state.getGameMode() == 22) {
                return "Career Round";
            } else if (state.getNetworkGame() == 1) {
                return "Multiplayer Round";
            } else {
                return "Career/Multiplayer Round";
            }
        }
        return SESSION_TYPES.getOrDefault((int) state.getSessionType(), "Unknown Session (ID " + state.getSessionType() + ")");
    }
}
