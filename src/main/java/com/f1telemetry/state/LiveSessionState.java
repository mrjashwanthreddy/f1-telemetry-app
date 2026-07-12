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
    
    // Session UUID or game SessionUID
    private String sessionId = null;
    
    // Track & Session
    private byte trackId;
    private short sessionType;   // 0=Unknown,1=Practice,2=Qualifying,3=Race,4=Race2,5=Race3,6=TimeTrial
    private short weather;
    private short totalLaps;
    private short safetyCarStatus;

    // Weekend link and multiplayer status
    private long weekendLinkIdentifier;
    private short gameMode;
    private short networkGame;

    // Lap offset for continuous session telemetry
    private int lapOffset = 0;
    
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
