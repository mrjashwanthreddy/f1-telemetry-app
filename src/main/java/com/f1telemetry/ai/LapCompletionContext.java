package com.f1telemetry.ai;

import lombok.Getter;

/**
 * Carries all data needed to build a post-lap AI alert prompt.
 * Populated by AiLapAlertService after each lap completion.
 */
@Getter
public class LapCompletionContext {

    private final String trackName;
    private final String sessionType;
    private final int lapNumber;

    // Completed lap times (ms)
    private final int totalLapTimeMs;
    private final int sector1Ms;
    private final int sector2Ms;
    private final int sector3Ms;

    // Previous lap for delta comparison (null if first lap)
    private final Integer prevTotalMs;
    private final Integer prevS1Ms;
    private final Integer prevS2Ms;
    private final Integer prevS3Ms;

    // Personal best for the session (null if this is the first completed lap)
    private final Integer personalBestMs;

    // Tire surface temperatures at lap end: [RL, RR, FL, FR] in Celsius
    private final short[] tyreSurfaceTemps;

    public LapCompletionContext(String trackName, String sessionType, int lapNumber,
                                int totalLapTimeMs, int sector1Ms, int sector2Ms, int sector3Ms,
                                Integer prevTotalMs, Integer prevS1Ms, Integer prevS2Ms, Integer prevS3Ms,
                                Integer personalBestMs, short[] tyreSurfaceTemps) {
        this.trackName = trackName;
        this.sessionType = sessionType;
        this.lapNumber = lapNumber;
        this.totalLapTimeMs = totalLapTimeMs;
        this.sector1Ms = sector1Ms;
        this.sector2Ms = sector2Ms;
        this.sector3Ms = sector3Ms;
        this.prevTotalMs = prevTotalMs;
        this.prevS1Ms = prevS1Ms;
        this.prevS2Ms = prevS2Ms;
        this.prevS3Ms = prevS3Ms;
        this.personalBestMs = personalBestMs;
        this.tyreSurfaceTemps = tyreSurfaceTemps;
    }

    /** Returns true if this lap is faster than the previous one. */
    public boolean isFasterThanPrev() {
        return prevTotalMs != null && totalLapTimeMs < prevTotalMs;
    }

    /** Returns true if this lap is a new session personal best. */
    public boolean isPersonalBest() {
        return personalBestMs == null || totalLapTimeMs <= personalBestMs;
    }

    /** Delta in ms vs previous lap. Positive = slower, Negative = faster. */
    public int getDeltaVsPrevMs() {
        return prevTotalMs != null ? totalLapTimeMs - prevTotalMs : 0;
    }
}
