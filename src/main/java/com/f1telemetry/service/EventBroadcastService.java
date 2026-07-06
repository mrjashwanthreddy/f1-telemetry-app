package com.f1telemetry.service;

import com.f1telemetry.engine.AlertEvent;
import com.f1telemetry.packets.EventDataDetails;
import com.f1telemetry.packets.PacketEventData;
import com.f1telemetry.state.LiveSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Decodes F1 25 Event packets (packetId=3) and pushes meaningful AlertEvent
 * messages to the /topic/live-alerts WebSocket topic so the frontend can
 * display real-time notifications for key race events.
 *
 * Supported events from the Jeddah time-trial data:
 *   SSTA — Session Started
 *   FTLP — Fastest Lap set
 *   SPTP — Speed Trap triggered
 *   PENA — Penalty issued
 *   DRSE — DRS Enabled
 *   DRSD — DRS Disabled
 *   RDFL — Red Flag
 *   FLBK — Flashback used
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveSessionState liveSessionState;

    // ── Penalty type lookup (F1 25 spec) ──────────────────────────────────────
    private static final String[] PENALTY_TYPES = {
        "Drive Through", "Stop & Go (5s)", "Stop & Go (10s)", "Stop & Go (15s)",
        "Stop & Go (20s)", "Grid Penalty", "Penalty Reminder", "Time Penalty",
        "Warning", "Disqualified", "Removed From Formation Lap", "Parked Too Long Timer",
        "Time Penalty"    // 12 = 5s time penalty (seen in Jeddah data)
    };

    // ── Infringement type lookup (F1 25 spec — partial) ──────────────────────
    private static final String[] INFRINGEMENT_TYPES = {
        "Blocking", "Blocking in Pit Lane", "Failing to Slow for Yellow", "Failing to Slow for SC",
        "Failing to Slow for VSC", "Overtake Under Safety Car", "Overtake Under Virtual Safety Car",
        "Exceeding Track Limits", "Exceeding Track Limits 3 Times", "Exceeding Track Limits Multiple",
        "Ignoring Blue Flags", "Rain Lights Not On", "Ignoring Drive Through", "Ignoring Time Penalty",
        "Unserved Drive Through Penalty", "Unserved Stop & Go Penalty", "Pit Lane Speeding",
        "Parked in Dangerous Position", "Incorrect Start Procedure", "Exceeded Maximum Lap Time",
        "Ignoring Penalisation Warning", "Not Pitting Under Safety Car", "Article 25.1 And 25.2 Violation",
        "Article 38.2 Violation", "Wheel Nut Loose", "Pit Lane Entry", "Corner Cutting"  // 26 = Corner Cutting (confirmed from Jeddah)
    };

    /**
     * Main entry point. Called by UdpPacketHandler whenever a PacketEventData arrives.
     */
    public void handleEvent(PacketEventData event) {
        String code = event.getEventStringCode();
        EventDataDetails d = event.getEventDetails();
        long now = System.currentTimeMillis();
        int playerIdx = liveSessionState.getPlayerCarIndex();

        AlertEvent alert = switch (code) {
            case PacketEventData.SESSION_STARTED -> new AlertEvent(
                "SESSION_START",
                "🏁 Session Started",
                "INFO",
                now,
                null
            );

            case PacketEventData.FASTEST_LAP -> {
                // Only surface the notification (all cars can set fastest lap in race mode)
                String lapTime = formatLapTime(d.getFastestLapTime());
                String who = (d.getFastestLapVehicleIdx() == playerIdx) ? "YOU — " : "Car #" + d.getFastestLapVehicleIdx() + " — ";
                yield new AlertEvent(
                    "FASTEST_LAP",
                    "🏆 FASTEST LAP — " + who + lapTime,
                    "SUCCESS",
                    now,
                    lapTime
                );
            }

            case PacketEventData.SPEED_TRAP -> {
                // Only notify for the player car; skip opponents to avoid noise
                if (d.getSpeedTrapVehicleIdx() != playerIdx) yield null;
                String speed = String.format("%.1f", d.getSpeedTrapSpeed());
                yield new AlertEvent(
                    "SPEED_TRAP",
                    "⚡ Speed Trap: " + speed + " km/h",
                    "INFO",
                    now,
                    speed
                );
            }

            case PacketEventData.PENALTY_ISSUED -> {
                String penaltyLabel = decodePenaltyType(d.getPenaltyType());
                String infringementLabel = decodeInfringementType(d.getInfringementType());
                String lapInfo = d.getPenaltyLapNum() > 0 ? " (Lap " + d.getPenaltyLapNum() + ")" : "";
                yield new AlertEvent(
                    "PENALTY",
                    "⚠️ " + penaltyLabel + " — " + infringementLabel + lapInfo,
                    "CRITICAL",
                    now,
                    infringementLabel
                );
            }

            case PacketEventData.DRS_ENABLED -> new AlertEvent(
                "DRS",
                "DRS ENABLED",
                "INFO",
                now,
                "OPEN"
            );

            case PacketEventData.DRS_DISABLED -> new AlertEvent(
                "DRS",
                "DRS DISABLED",
                "WARNING",
                now,
                "CLOSED"
            );

            case PacketEventData.RED_FLAG -> new AlertEvent(
                "RED_FLAG",
                "🚩 RED FLAG",
                "CRITICAL",
                now,
                null
            );

            case PacketEventData.FLASHBACK -> new AlertEvent(
                "FLASHBACK",
                "⏪ Flashback Used",
                "WARNING",
                now,
                null
            );

            case PacketEventData.CHEQUERED_FLAG -> new AlertEvent(
                "CHEQUERED_FLAG",
                "🏁 Chequered Flag",
                "SUCCESS",
                now,
                null
            );

            case PacketEventData.SAFETY_CAR -> {
                String scType = switch (d.getSafetyCarType()) {
                    case 1 -> "Safety Car";
                    case 2 -> "Virtual Safety Car";
                    case 3 -> "Formation Lap";
                    default -> "Safety Car";
                };
                String scEvent = switch (d.getSafetyCarEventType()) {
                    case 0 -> "DEPLOYED";
                    case 1 -> "RETURNING";
                    case 2 -> "RETURNED";
                    case 3 -> "RESUME";
                    default -> "";
                };
                yield new AlertEvent(
                    "SAFETY_CAR",
                    "🟡 " + scType + " " + scEvent,
                    "WARNING",
                    now,
                    scType
                );
            }

            // BUTN and other high-frequency or unneeded events — ignore
            default -> null;
        };

        if (alert != null) {
            log.info("[EventBroadcast] {} — {}", alert.getType(), alert.getMessage());
            messagingTemplate.convertAndSend("/topic/live-alerts", alert);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Formats a lap time in seconds (float) to "M:SS.mmm" or "SS.mmm".
     * e.g. 93.099 → "1:33.099"
     */
    private static String formatLapTime(float lapTimeSeconds) {
        int totalMs = Math.round(lapTimeSeconds * 1000);
        int minutes = totalMs / 60000;
        int secs = (totalMs % 60000) / 1000;
        int millis = totalMs % 1000;
        if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, secs, millis);
        } else {
            return String.format("%d.%03d", secs, millis);
        }
    }

    private static String decodePenaltyType(short code) {
        if (code >= 0 && code < PENALTY_TYPES.length) return PENALTY_TYPES[code];
        return "Penalty";
    }

    private static String decodeInfringementType(short code) {
        if (code >= 0 && code < INFRINGEMENT_TYPES.length) return INFRINGEMENT_TYPES[code];
        return "Infringement";
    }
}
