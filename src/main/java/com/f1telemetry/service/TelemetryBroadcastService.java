package com.f1telemetry.service;

import com.f1telemetry.state.LiveSessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reads the LiveSessionState memory snapshot and broadcasts it to the WebSocket clients at 30Hz.
 */
@Slf4j
@Service
@EnableScheduling
public class TelemetryBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveSessionState liveSessionState;
    private final com.f1telemetry.engine.RuleEvaluationEngine ruleEngine;
    private final com.f1telemetry.service.PreferenceService preferenceService;
    private final com.f1telemetry.service.AsyncPersistenceService asyncPersistenceService;
    private final com.f1telemetry.service.ActiveUserService activeUserService;

    public TelemetryBroadcastService(SimpMessagingTemplate messagingTemplate, 
                                     LiveSessionState liveSessionState,
                                     com.f1telemetry.engine.RuleEvaluationEngine ruleEngine,
                                     com.f1telemetry.service.PreferenceService preferenceService,
                                     com.f1telemetry.service.AsyncPersistenceService asyncPersistenceService,
                                     com.f1telemetry.service.ActiveUserService activeUserService) {
        this.messagingTemplate = messagingTemplate;
        this.liveSessionState = liveSessionState;
        this.ruleEngine = ruleEngine;
        this.preferenceService = preferenceService;
        this.asyncPersistenceService = asyncPersistenceService;
        this.activeUserService = activeUserService;
    }

    /**
     * Broadcasts the LiveSessionState object at exactly 33ms intervals (~30Hz).
     * This decouples the 60Hz UDP ingestion rate from the web UI refresh rate, preventing browser freeze.
     */
    @Scheduled(fixedRate = 33)
    public void broadcastTelemetry() {
        try {
            // Queue frame for async database write only if data is active
            if (System.currentTimeMillis() - liveSessionState.getLastUpdateTime() < 1000) {
                asyncPersistenceService.enqueueTelemetryFrame(liveSessionState);
            }

            // Broadcast live telemetry to dashboard
            messagingTemplate.convertAndSend("/topic/live-telemetry", liveSessionState);

            // Fetch user preferences dynamically for the active logged-in driver
            com.f1telemetry.domain.UserPreference prefs = null;
            com.f1telemetry.domain.User activeUser = activeUserService.getActiveUser();
            if (activeUser != null) {
                try {
                    prefs = preferenceService.getPreferences(activeUser.getUsername());
                } catch (Exception e) {
                    // Ignore if user doesn't exist yet
                }
            }

            // Evaluate Rules
            java.util.List<com.f1telemetry.engine.AlertEvent> alerts = ruleEngine.evaluate(liveSessionState, prefs);
            for (com.f1telemetry.engine.AlertEvent alert : alerts) {
                messagingTemplate.convertAndSend("/topic/live-alerts", alert);
                log.warn("ALERT TRIGGERED: {} - {}", alert.getType(), alert.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to broadcast telemetry via WebSocket", e);
        }
    }
}
