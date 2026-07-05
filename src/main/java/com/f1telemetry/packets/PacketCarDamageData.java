package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Car Damage Data - damage status for all 22 cars.
 * Packet ID: 10
 * Frequency: 2 per second
 */
@Data
@NoArgsConstructor
public class PacketCarDamageData {
    private PacketHeader header;
    private CarDamageData[] carDamageData = new CarDamageData[22];

    public static final int PACKET_ID = 10;
}
