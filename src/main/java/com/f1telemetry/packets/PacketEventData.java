package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Event Data - various notable events during a session.
 * Packet ID: 3
 * Size: 45 bytes
 * Frequency: When the event occurs
 */
@Data
@NoArgsConstructor
public class PacketEventData {
    private PacketHeader header;
    private String eventStringCode;         // 4 characters (e.g., "SSTA", "FTLP", "RTMT")
    private EventDataDetails eventDetails;

    public static final int PACKET_ID = 3;

    // Event code constants
    public static final String SESSION_STARTED = "SSTA";
    public static final String SESSION_ENDED = "SEND";
    public static final String FASTEST_LAP = "FTLP";
    public static final String RETIREMENT = "RTMT";
    public static final String DRS_ENABLED = "DRSE";
    public static final String DRS_DISABLED = "DRSD";
    public static final String TEAM_MATE_IN_PITS = "TMPT";
    public static final String CHEQUERED_FLAG = "CHQF";
    public static final String RACE_WINNER = "RCWN";
    public static final String PENALTY_ISSUED = "PENA";
    public static final String SPEED_TRAP = "SPTP";
    public static final String START_LIGHTS = "STLG";
    public static final String LIGHTS_OUT = "LGOT";
    public static final String DRIVE_THROUGH_SERVED = "DTSV";
    public static final String STOP_GO_SERVED = "SGSV";
    public static final String FLASHBACK = "FLBK";
    public static final String BUTTON_STATUS = "BUTN";
    public static final String RED_FLAG = "RDFL";
    public static final String OVERTAKE = "OVTK";
    public static final String SAFETY_CAR = "SCAR";
    public static final String COLLISION = "COLL";
}
