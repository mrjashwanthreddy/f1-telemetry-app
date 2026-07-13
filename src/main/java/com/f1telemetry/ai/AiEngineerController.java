package com.f1telemetry.ai;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.engine.RuleEvaluationEngine;
import com.f1telemetry.repository.LapTimeRecordRepository;
import com.f1telemetry.repository.RaceSessionRepository;
import com.f1telemetry.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for the AI Race Engineer features.
 *
 * POST /api/ai/session-debrief/{sessionId}   → Full post-session AI debrief
 * POST /api/ai/chat                           → Session chat bot Q&A
 * GET  /api/ai/status                         → Whether AI is configured (key present)
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiEngineerController {

    private final AiEngineerService aiEngineerService;
    private final LapTimeRecordRepository lapRepository;
    private final RaceSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CornerAnalysisService cornerAnalysisService;
    private final GlobalHotkeyService globalHotkeyService;
    private final com.f1telemetry.service.PreferenceService preferenceService;

    // ── Status ─────────────────────────────────────────────────────────────────

    /**
     * Frontend calls this on load to decide whether to show AI features.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean activeEnabled = false;
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            if (username != null && !username.equals("anonymousUser")) {
                activeEnabled = preferenceService.getPreferences(username).isAiEnabled();
            }
        } catch (Exception e) {
            // ignore
        }

        return ResponseEntity.ok(Map.of(
            "aiConfigured", aiEngineerService.isConfigured(),
            "model", aiEngineerService.getModelName(),
            "aiEnabled", activeEnabled
        ));
    }



    /**
     * Puts the global hotkey service into "bind mode" to record the next keypress.
     */
    @PostMapping("/hotkey/bind")
    public ResponseEntity<Map<String, String>> enterHotkeyBindMode() {
        globalHotkeyService.enterBindMode();
        return ResponseEntity.ok(Map.of("status", "waiting-for-keypress"));
    }

    /**
     * Updates the hotkey from a keypress captured in the browser.
     */
    @PostMapping("/hotkey/bind-keypress")
    public ResponseEntity<Map<String, String>> bindKeyPress(@RequestBody Map<String, Object> payload) {
        int keyCode = ((Number) payload.get("keyCode")).intValue();
        String keyLabel = (String) payload.get("keyLabel");
        
        // Disable backend bind mode
        globalHotkeyService.setBindMode(false);
        
        // Save preferences and broadcast the event
        globalHotkeyService.saveNewHotkeyPreference(keyCode, keyLabel);
        
        return ResponseEntity.ok(Map.of("status", "success", "keyLabel", keyLabel));
    }

    // ── Session Debrief ────────────────────────────────────────────────────────

    /**
     * Generates a full post-session AI debrief for N laps.
     * The user clicks "AI Debrief" in the session history tab.
     */
    @PostMapping("/session-debrief/{sessionId}")
    public ResponseEntity<?> generateSessionDebrief(@PathVariable String sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        com.f1telemetry.domain.UserPreference prefs = preferenceService.getPreferences(username);
        if (prefs == null || !prefs.isAiEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "AI features are disabled in settings."
            ));
        }

        Optional<RaceSession> sessionOpt = userRepository.findByUsername(username)
            .flatMap(user -> sessionRepository.findBySessionId(sessionId)
                .filter(s -> s.getUser().getId().equals(user.getId())));

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RaceSession session = sessionOpt.get();
        List<LapTimeRecord> laps = lapRepository.findByRaceSessionOrderByLapNumberAsc(session);

        if (laps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No completed laps found for this session."
            ));
        }

        // Build lap data list: {lapNum, s1, s2, s3, total}
        List<int[]> lapData = laps.stream()
            .filter(l -> l.getTotalLapTimeInMS() > 0)
            .map(l -> new int[]{
                l.getLapNumber(),
                l.getSector1TimeInMS(),
                l.getSector2TimeInMS(),
                l.getSector3TimeInMS(),
                l.getTotalLapTimeInMS()
            })
            .toList();

        // Find best and slowest laps
        int bestLapNum = -1;
        int bestLapTime = Integer.MAX_VALUE;
        int slowestLapNum = -1;
        int slowestLapTime = 0;

        for (LapTimeRecord l : laps) {
            int t = l.getTotalLapTimeInMS();
            if (t > 0) {
                if (t < bestLapTime) {
                    bestLapTime = t;
                    bestLapNum = l.getLapNumber();
                }
                if (t > slowestLapTime) {
                    slowestLapTime = t;
                    slowestLapNum = l.getLapNumber();
                }
            }
        }

        // Calculate corner telemetry comparison deltas
        String cornerAnalysis = "";
        if (bestLapNum != -1 && slowestLapNum != -1 && bestLapNum != slowestLapNum) {
            cornerAnalysis = cornerAnalysisService.buildComparisonSummary(
                sessionId, bestLapNum, slowestLapNum, session.getTrackName());
        }

        // Build tyre+fuel context for enhanced debrief
        String tyreContext = buildTyreContext(laps);

        String debrief = aiEngineerService.generateSessionDebrief(
            session.getTrackName(), session.getSessionType(), lapData, cornerAnalysis);

        if (debrief == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "AI service unavailable. Check your GEMINI_API_KEY configuration."
            ));
        }

        return ResponseEntity.ok(Map.of(
            "sessionId", sessionId,
            "track", session.getTrackName(),
            "sessionType", session.getSessionType(),
            "lapCount", laps.size(),
            "debrief", debrief,
            "tyreContext", tyreContext,
            "cornerAnalysis", cornerAnalysis,
            "laps", laps.stream().map(this::lapToMap).toList()
        ));
    }

    // ── Chat Q&A ───────────────────────────────────────────────────────────────

    /**
     * Answers a natural language question about a selected session.
     * Called from the chat panel when the user types or speaks a question.
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        com.f1telemetry.domain.UserPreference prefs = preferenceService.getPreferences(username);
        if (prefs == null || !prefs.isAiEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "AI features are disabled in settings."
            ));
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be empty."));
        }

        Optional<RaceSession> sessionOpt = userRepository.findByUsername(username)
            .flatMap(user -> sessionRepository.findBySessionId(request.getSessionId())
                .filter(s -> s.getUser().getId().equals(user.getId())));

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RaceSession session = sessionOpt.get();
        List<LapTimeRecord> laps = lapRepository.findByRaceSessionOrderByLapNumberAsc(session);

        List<int[]> lapData = laps.stream()
            .filter(l -> l.getTotalLapTimeInMS() > 0)
            .map(l -> new int[]{
                l.getLapNumber(), l.getSector1TimeInMS(),
                l.getSector2TimeInMS(), l.getSector3TimeInMS(),
                l.getTotalLapTimeInMS()
            })
            .toList();

        // Build additional corner telemetry context for chat questions
        int bestLapNum = -1;
        int bestLapTime = Integer.MAX_VALUE;
        for (LapTimeRecord l : laps) {
            int t = l.getTotalLapTimeInMS();
            if (t > 0 && t < bestLapTime) {
                bestLapTime = t;
                bestLapNum = l.getLapNumber();
            }
        }

        String cornerContext = "";
        if (bestLapNum != -1 && laps.size() > 1) {
            // Compare best lap vs average/last lap if possible
            int compLapNum = laps.get(laps.size() - 1).getLapNumber();
            if (compLapNum == bestLapNum && laps.size() > 1) {
                compLapNum = laps.get(laps.size() - 2).getLapNumber();
            }
            cornerContext = cornerAnalysisService.buildComparisonSummary(
                request.getSessionId(), bestLapNum, compLapNum, session.getTrackName());
        }

        // Build additional tyre+fuel context for smarter answers
        String additionalContext = buildTyreContext(laps);
        if (!cornerContext.isBlank()) {
            additionalContext = additionalContext + "\n" + cornerContext;
        }

        String answer = aiEngineerService.answerChatQuestion(
            request.getQuestion(),
            session.getTrackName(),
            session.getSessionType(),
            lapData,
            additionalContext
        );

        if (answer == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "AI service unavailable."
            ));
        }

        return ResponseEntity.ok(Map.of(
            "question", request.getQuestion(),
            "answer", answer,
            "sessionId", request.getSessionId(),
            "track", session.getTrackName()
        ));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Builds a readable tyre+fuel summary for the AI prompt context. */
    private String buildTyreContext(List<LapTimeRecord> laps) {
        if (laps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Tyre wear and fuel per lap:\n");
        for (LapTimeRecord lap : laps) {
            if (lap.getTyreWearFL() > 0 || lap.getFuelRemainingKg() > 0) {
                sb.append(String.format(
                    "  Lap %d: Wear FL=%.1f%% FR=%.1f%% RL=%.1f%% RR=%.1f%% | Fuel=%.2fkg | TyreAge=%d laps\n",
                    lap.getLapNumber(),
                    lap.getTyreWearFL(), lap.getTyreWearFR(),
                    lap.getTyreWearRL(), lap.getTyreWearRR(),
                    lap.getFuelRemainingKg(), lap.getTyresAgeLaps()
                ));
            }
        }
        return sb.toString();
    }

    /** Converts a LapTimeRecord to a Map for JSON serialisation. */
    private Map<String, Object> lapToMap(LapTimeRecord l) {
        return Map.of(
            "lapNumber", l.getLapNumber(),
            "sector1", l.getSector1TimeInMS(),
            "sector2", l.getSector2TimeInMS(),
            "sector3", l.getSector3TimeInMS(),
            "total", l.getTotalLapTimeInMS(),
            "tyreWearFL", l.getTyreWearFL(),
            "tyreWearFR", l.getTyreWearFR(),
            "tyreWearRL", l.getTyreWearRL(),
            "tyreWearRR", l.getTyreWearRR(),
            "fuelRemaining", l.getFuelRemainingKg()
        );
    }

    /** Request body for the chat endpoint. */
    @lombok.Data
    public static class ChatRequest {
        private String question;
        private String sessionId;
        private String scope; // "single-session", "all-sessions"
    }
}
