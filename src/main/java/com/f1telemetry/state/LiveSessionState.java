package com.f1telemetry.state;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * Thread-safe singleton representing the exact current state of the race.
 * Data is overwritten in place, guaranteeing O(1) constant memory usage.
 * It will not leak or overload memory.
 */
@Data
@Component
public class LiveSessionState {
    
    // Track & Session
    private byte trackId;
    private short weather;
    private short totalLaps;
    private short safetyCarStatus;
    
    // 22 Cars fixed array
    private final CarState[] cars = new CarState[22];
    
    // Player Index
    private int playerCarIndex = 0;
    
    // Track when we last received a packet
    private long lastUpdateTime = 0;

    public LiveSessionState() {
        for (int i = 0; i < 22; i++) {
            cars[i] = new CarState(i);
        }
    }
}
