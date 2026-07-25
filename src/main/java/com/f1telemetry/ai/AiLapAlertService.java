package com.f1telemetry.ai;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.engine.AlertEvent;
import com.f1telemetry.engine.RuleEvaluationEngine;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.state.CarState;
import com.f1telemetry.state.LiveSessionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Fires an AI-powered lap verdict after every completed lap.
 *
 * Called ASYNC from RuleEvaluationEngine — zero impact on the 60Hz telemetry pipeline.
 * Broadcasts a LAP_DEBRIEF AlertEvent via WebSocket which the frontend displays
 * as a toast and reads aloud via Web Speech API TTS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLapAlertService {

    private final AiEngineerService aiEngineerService;
    private final LapTimeRecordRepository lapRepository;
    private final RaceSessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Called asynchronously after each lap is saved to the database.
     * Fetches lap history, calls AI, and pushes a LAP_DEBRIEF alert.
     *
     * @param lapNum      the just-completed lap number (db-offset)
     * @param s1Ms        sector 1 time in ms
     * @param s2Ms        sector 2 time in ms
     * @param s3Ms        sector 3 time in ms
     * @param totalMs     total lap time in ms
     * @param state       current live session state (for tire temps, track info)
     */
    @Async("aiTaskExecutor")
    public void fireLapAlertAsync(int lapNum, int s1Ms, int s2Ms, int s3Ms, int totalMs,
                                   LiveSessionState state) {
        try {
            String dbSessionId = RuleEvaluationEngine.getDbSessionId(state);
            String trackName = RuleEvaluationEngine.resolveTrackName(state.getTrackId());
            String sessionType = RuleEvaluationEngine.resolveSessionType(state);

            // Fetch session and lap history for comparison
            Optional<RaceSession> sessionOpt = sessionRepository.findBySessionId(dbSessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("[AI Lap Alert] Session {} not found in DB — skipping lap {} alert", dbSessionId, lapNum);
                return;
            }

            List<LapTimeRecord> allLaps = lapRepository
                .findByRaceSessionOrderByLapNumberAsc(sessionOpt.get());

            // Find personal best (excluding current lap — it's just been saved)
            Integer personalBestMs = allLaps.stream()
                .filter(l -> l.getTotalLapTimeInMS() > 0 && l.getLapNumber() != lapNum)
                .mapToInt(LapTimeRecord::getTotalLapTimeInMS)
                .min()
                .stream().boxed().findFirst().orElse(null);

            // Check if current lap IS the personal best (including itself)
            if (personalBestMs == null || totalMs <= personalBestMs) {
                personalBestMs = null; // null signals "this IS the PB"
            }

            // Find previous lap
            LapTimeRecord prevLap = allLaps.stream()
                .filter(l -> l.getLapNumber() == lapNum - 1 && l.getTotalLapTimeInMS() > 0)
                .findFirst().orElse(null);

            // Get player's tire surface temps
            int playerIdx = state.getPlayerCarIndex();
            CarState playerCar = state.getCars()[playerIdx];
            short[] tireTemps = playerCar.getTyreSurfaceTemps().clone();

            // Build context
            LapCompletionContext ctx = new LapCompletionContext(
                trackName, sessionType, lapNum,
                totalMs, s1Ms, s2Ms, s3Ms,
                prevLap != null ? prevLap.getTotalLapTimeInMS() : null,
                prevLap != null ? prevLap.getSector1TimeInMS() : null,
                prevLap != null ? prevLap.getSector2TimeInMS() : null,
                prevLap != null ? prevLap.getSector3TimeInMS() : null,
                personalBestMs,
                tireTemps
            );

            // Call AI (may take 1-3s — async, so this is fine)
            String aiVerdict = aiEngineerService.analyzeLapCompletion(ctx);

            // Build the structured detail JSON for the frontend
            String lapTimeFormatted = PromptBuilder.formatMs(totalMs);
            boolean isPB = ctx.isPersonalBest();
            String deltaStr = prevLap != null
                ? ((ctx.getDeltaVsPrevMs() >= 0 ? "+" : "") +
                   String.format("%.3f", ctx.getDeltaVsPrevMs() / 1000.0) + "s")
                : null;

            String detailJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("lapNum", lapNum);
                put("lapTime", lapTimeFormatted);
                put("isPB", isPB);
                put("delta", deltaStr);
                put("s1", PromptBuilder.formatMs(s1Ms));
                put("s2", PromptBuilder.formatMs(s2Ms));
                put("s3", PromptBuilder.formatMs(s3Ms));
                if (prevLap != null) {
                    put("s1Delta", formatDelta(s1Ms - prevLap.getSector1TimeInMS()));
                    put("s2Delta", formatDelta(s2Ms - prevLap.getSector2TimeInMS()));
                    put("s3Delta", formatDelta(s3Ms - prevLap.getSector3TimeInMS()));
                }
                put("aiVerdict", aiVerdict != null ? aiVerdict : buildFallbackVerdict(ctx));
            }});

            // Determine severity for the toast styling
            String severity = isPB ? "SUCCESS" : (ctx.isFasterThanPrev() ? "INFO" : "WARNING");
            String message = buildToastTitle(ctx, lapTimeFormatted);

            AlertEvent alert = new AlertEvent("LAP_DEBRIEF", message, severity,
                System.currentTimeMillis(), detailJson);

            log.info("[AI Lap Alert] Broadcasting LAP_DEBRIEF for Lap {} — {} (session: {}, track: {})", 
                    lapNum, lapTimeFormatted, dbSessionId, trackName);
            messagingTemplate.convertAndSend("/topic/live-alerts", alert);

        } catch (Exception e) {
            log.error("[AI Lap Alert] Error generating lap alert for lap {}: {}", lapNum, e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildToastTitle(LapCompletionContext ctx, String lapTime) {
        if (ctx.isPersonalBest()) {
            return "🏆 LAP " + ctx.getLapNumber() + " — " + lapTime + " — NEW PERSONAL BEST!";
        } else if (ctx.isFasterThanPrev()) {
            return "🟢 LAP " + ctx.getLapNumber() + " — " + lapTime + " (improved)";
        } else {
            return "🔴 LAP " + ctx.getLapNumber() + " — " + lapTime + " (slower)";
        }
    }

    private String buildFallbackVerdict(LapCompletionContext ctx) {
        // Rule-based fallback when AI is unavailable
        if (ctx.isPersonalBest()) {
            return "New personal best! Keep up the consistency.";
        } else if (ctx.isFasterThanPrev()) {
            return "Lap improved by " +
                String.format("%.3f", Math.abs(ctx.getDeltaVsPrevMs()) / 1000.0) + "s. Good progress.";
        } else if (ctx.getPrevTotalMs() != null) {
            return "Lap was " +
                String.format("%.3f", ctx.getDeltaVsPrevMs() / 1000.0) +
                "s slower than previous. Focus on sector 2.";
        }
        return "Lap complete. Keep pushing.";
    }

    private static String formatDelta(int deltaMs) {
        if (deltaMs == 0) return "even";
        return (deltaMs > 0 ? "+" : "") + String.format("%.3fs", deltaMs / 1000.0);
    }
}
