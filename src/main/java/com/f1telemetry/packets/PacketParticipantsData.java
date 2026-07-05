package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Participants Data - list of participants in the race.
 * Packet ID: 4
 * Size: 1284 bytes
 * Frequency: Every 5 seconds
 */
@Data
@NoArgsConstructor
public class PacketParticipantsData {
    private PacketHeader header;
    private short numActiveCars;                        // uint8
    private ParticipantData[] participants = new ParticipantData[22];

    public static final int PACKET_ID = 4;
}
