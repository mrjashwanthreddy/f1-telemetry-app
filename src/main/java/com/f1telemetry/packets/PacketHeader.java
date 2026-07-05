package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * F1 25 Packet Header - 29 bytes
 * Present at the start of every UDP packet.
 * All values are Little-Endian encoded.
 */
@Data
@NoArgsConstructor
public class PacketHeader {
    private int packetFormat;           // uint16 - 2025
    private short gameYear;             // uint8  - last two digits e.g. 25
    private short gameMajorVersion;     // uint8  - "X.00"
    private short gameMinorVersion;     // uint8  - "1.XX"
    private short packetVersion;        // uint8  - Version of this packet type
    private short packetId;             // uint8  - Identifier for the packet type (0-15)
    private long sessionUID;            // uint64 - Unique identifier for the session
    private float sessionTime;          // float  - Session timestamp
    private long frameIdentifier;       // uint32 - Identifier for the frame
    private long overallFrameIdentifier;// uint32 - Overall frame identifier (no flashback reset)
    private short playerCarIndex;       // uint8  - Index of player's car in the array
    private short secondaryPlayerCarIndex; // uint8 - Index of secondary player's car (255 if none)

    /** Total size of the header in bytes */
    public static final int SIZE = 29;
}
