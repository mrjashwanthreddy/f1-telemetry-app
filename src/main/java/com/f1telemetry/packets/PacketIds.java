package com.f1telemetry.packets;

/**
 * Constants for F1 25 Packet IDs.
 * Maps packet ID byte to packet type for routing.
 */
public final class PacketIds {
    private PacketIds() {} // Utility class

    public static final int MOTION = 0;
    public static final int SESSION = 1;
    public static final int LAP_DATA = 2;
    public static final int EVENT = 3;
    public static final int PARTICIPANTS = 4;
    public static final int CAR_SETUPS = 5;
    public static final int CAR_TELEMETRY = 6;
    public static final int CAR_STATUS = 7;
    public static final int FINAL_CLASSIFICATION = 8;
    public static final int LOBBY_INFO = 9;
    public static final int CAR_DAMAGE = 10;
    public static final int SESSION_HISTORY = 11;
    public static final int TYRE_SETS = 12;
    public static final int MOTION_EX = 13;
    public static final int TIME_TRIAL = 14;
    public static final int LAP_POSITIONS = 15;
}
