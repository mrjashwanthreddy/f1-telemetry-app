package com.f1telemetry.ai;

import java.util.List;

/**
 * Builds structured, data-grounded prompts for the Gemini AI API.
 * All prompts are based on real telemetry numbers to prevent hallucinations.
 */
public class PromptBuilder {

    private static final String ENGINEER_PERSONA =
        "You are an experienced F1 race engineer giving radio feedback to your driver. " +
        "Be concise, technical, and direct. Use F1 engineering language.";

    /**
     * Builds a short post-lap radio-call prompt (max 2 sentences response expected).
     * Fired automatically after every completed lap.
     */
    public static String buildLapAlertPrompt(LapCompletionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(ENGINEER_PERSONA).append("\n\n");
        sb.append("Track: ").append(ctx.getTrackName())
          .append(" | Session: ").append(ctx.getSessionType()).append("\n");
        sb.append("Completed Lap ").append(ctx.getLapNumber())
          .append(": ").append(formatMs(ctx.getTotalLapTimeMs())).append("\n");

        if (ctx.getPrevTotalMs() != null) {
            int delta = ctx.getDeltaVsPrevMs();
            String deltaStr = (delta >= 0 ? "+" : "") + String.format("%.3f", delta / 1000.0) + "s";
            sb.append("vs Previous Lap: ").append(formatMs(ctx.getPrevTotalMs()))
              .append(" (").append(deltaStr).append(")\n");
            sb.append("Sector deltas vs previous: S1 ")
              .append(formatDelta(ctx.getSector1Ms() - ctx.getPrevS1Ms()))
              .append(", S2 ").append(formatDelta(ctx.getSector2Ms() - ctx.getPrevS2Ms()))
              .append(", S3 ").append(formatDelta(ctx.getSector3Ms() - ctx.getPrevS3Ms())).append("\n");
        } else {
            sb.append("(First completed lap — no previous lap to compare)\n");
        }

        if (ctx.isPersonalBest()) {
            sb.append("Status: NEW SESSION PERSONAL BEST\n");
        }

        short[] temps = ctx.getTyreSurfaceTemps();
        if (temps != null && temps.length == 4) {
            sb.append(String.format("Tire surface temps: FL=%d°C FR=%d°C RL=%d°C RR=%d°C (optimal: 85-100°C)\n",
                temps[2], temps[3], temps[0], temps[1]));
        }

        sb.append("\nGive EXACTLY 2 sentences as a radio-call verdict. ");
        if (ctx.isPersonalBest()) {
            sb.append("Acknowledge the personal best, then give one key insight about what to maintain or improve.");
        } else if (ctx.isFasterThanPrev()) {
            sb.append("Identify what improved and give one concrete tip to continue improving.");
        } else {
            sb.append("Identify the biggest cause of the time loss and give one concrete action to recover it next lap.");
        }

        return sb.toString();
    }

    /**
     * Builds a full session debrief prompt for post-session analysis.
     */
    public static String buildSessionDebriefPrompt(String trackName, String sessionType,
                                                    List<int[]> laps, String cornerAnalysis) {
        // laps: each int[] = {lapNum, s1Ms, s2Ms, s3Ms, totalMs}
        StringBuilder sb = new StringBuilder();
        sb.append(ENGINEER_PERSONA).append("\n\n");
        sb.append("Produce a structured post-session debrief for the driver.\n\n");
        sb.append("Track: ").append(trackName).append(" | Session: ").append(sessionType).append("\n");
        sb.append("Session laps:\n");

        int bestLapMs = Integer.MAX_VALUE;
        int bestLapNum = -1;
        for (int[] lap : laps) {
            int lapNum = lap[0], s1 = lap[1], s2 = lap[2], s3 = lap[3], total = lap[4];
            sb.append(String.format("  Lap %d: %s (S1=%s S2=%s S3=%s)\n",
                lapNum, formatMs(total), formatMs(s1), formatMs(s2), formatMs(s3)));
            if (total > 0 && total < bestLapMs) { bestLapMs = total; bestLapNum = lapNum; }
        }

        sb.append("\nBest lap: Lap ").append(bestLapNum).append(" — ").append(formatMs(bestLapMs)).append("\n\n");

        if (cornerAnalysis != null && !cornerAnalysis.isBlank()) {
            sb.append("Corner Telemetry Comparison:\n").append(cornerAnalysis).append("\n\n");
        }

        sb.append("Structure your debrief as:\n");
        sb.append("1. PACE EVOLUTION — one sentence describing the pace trend across laps\n");
        sb.append("2. BEST LAP — one sentence about what made the best lap good\n");
        sb.append("3. CONSISTENCY — rate consistency on a 1-10 scale with brief reason\n");
        sb.append("4. SECTOR ANALYSIS — identify the weakest sector. If corner data is available, reference specific corners where speed or G-Force dropped\n");
        sb.append("5. 3 IMPROVEMENT POINTS — numbered list, each max 2 sentences, specific and actionable (reference specific corners/turns if applicable)\n");
        sb.append("6. NEXT SESSION TARGET — one sentence with a specific lap time target to aim for\n\n");
        sb.append("Be specific with lap times, sector deltas, and corner speeds. Use real numbers from the data above.");

        return sb.toString();
    }

    /**
     * Builds a chat Q&A prompt with session context.
     */
    public static String buildChatPrompt(String question, String trackName, String sessionType,
                                          List<int[]> laps, String additionalContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(ENGINEER_PERSONA).append("\n\n");
        sb.append("Session context:\n");
        sb.append("Track: ").append(trackName).append(" | Session: ").append(sessionType).append("\n");
        sb.append("Lap data:\n");

        for (int[] lap : laps) {
            sb.append(String.format("  Lap %d: %s (S1=%s S2=%s S3=%s)\n",
                lap[0], formatMs(lap[4]), formatMs(lap[1]), formatMs(lap[2]), formatMs(lap[3])));
        }

        if (additionalContext != null && !additionalContext.isBlank()) {
            sb.append("\nAdditional context:\n").append(additionalContext).append("\n");
        }

        sb.append("\nDriver's question: \"").append(question).append("\"\n\n");
        sb.append("Answer as the race engineer. Be concise and specific. Use real numbers from the data. Max 4 sentences.");

        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Formats milliseconds as "M:SS.mmm" (e.g. 97412 → "1:37.412") */
    public static String formatMs(int ms) {
        if (ms <= 0) return "N/A";
        int minutes = ms / 60000;
        int secs = (ms % 60000) / 1000;
        int millis = ms % 1000;
        return minutes > 0
            ? String.format("%d:%02d.%03d", minutes, secs, millis)
            : String.format("%d.%03d", secs, millis);
    }

    /** Formats a delta in ms with +/- sign (e.g. -312 → "-0.312s") */
    private static String formatDelta(int deltaMs) {
        if (deltaMs == 0) return "even";
        return (deltaMs > 0 ? "+" : "") + String.format("%.3fs", deltaMs / 1000.0);
    }
}
