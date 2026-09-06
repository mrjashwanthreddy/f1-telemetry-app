package com.f1telemetry.parser;

import com.f1telemetry.packets.CarDamageData;
import com.f1telemetry.packets.PacketCarDamageData;
import com.f1telemetry.packets.PacketHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class PacketDeserializerTest {

    @Test
    void testDeserializeCarDamageWithBlisters() {
        PacketDeserializer deserializer = new PacketDeserializer();

        // 1. Build Header (29 bytes)
        ByteBuffer headerBuf = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN);
        headerBuf.putShort((short) 2025); // m_packetFormat
        headerBuf.put((byte) 25);        // m_gameYear
        headerBuf.put((byte) 1);         // m_gameMajorVersion
        headerBuf.put((byte) 23);        // m_gameMinorVersion
        headerBuf.put((byte) 1);         // m_packetVersion
        headerBuf.put((byte) 10);        // m_packetId (Car Damage)
        headerBuf.putLong(123456789L);   // m_sessionUID
        headerBuf.putFloat(10.5f);       // m_sessionTime
        headerBuf.putInt(100);           // m_frameIdentifier
        headerBuf.putInt(100);           // m_overallFrameIdentifier
        headerBuf.put((byte) 0);         // m_playerCarIndex
        headerBuf.put((byte) 255);       // m_secondaryPlayerCarIndex

        // 2. Build 22 cars * 46 bytes = 1012 bytes
        ByteBuffer bodyBuf = ByteBuffer.allocate(1012).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 22; i++) {
            // tyresWear (4 * float = 16 bytes)
            bodyBuf.putFloat(10.0f);
            bodyBuf.putFloat(10.0f);
            bodyBuf.putFloat(10.0f);
            bodyBuf.putFloat(10.0f);

            // tyresDamage (4 * uint8 = 4 bytes)
            bodyBuf.put((byte) 1);
            bodyBuf.put((byte) 1);
            bodyBuf.put((byte) 1);
            bodyBuf.put((byte) 1);

            // brakesDamage (4 * uint8 = 4 bytes)
            bodyBuf.put((byte) 2);
            bodyBuf.put((byte) 2);
            bodyBuf.put((byte) 2);
            bodyBuf.put((byte) 2);

            // tyresBlisters (4 * uint8 = 4 bytes)
            bodyBuf.put((byte) 5);
            bodyBuf.put((byte) 6);
            bodyBuf.put((byte) 7);
            bodyBuf.put((byte) 8);

            // wing damages
            if (i == 0) {
                bodyBuf.put((byte) 25); // frontLeftWingDamage
                bodyBuf.put((byte) 35); // frontRightWingDamage
            } else {
                bodyBuf.put((byte) 0);
                bodyBuf.put((byte) 0);
            }
            bodyBuf.put((byte) 0); // rearWingDamage
            bodyBuf.put((byte) 0); // floorDamage
            bodyBuf.put((byte) 0); // diffuserDamage
            bodyBuf.put((byte) 0); // sidepodDamage
            bodyBuf.put((byte) 0); // drsFault
            bodyBuf.put((byte) 0); // ersFault
            bodyBuf.put((byte) 0); // gearBoxDamage
            bodyBuf.put((byte) 0); // engineDamage
            bodyBuf.put((byte) 0); // engineMGUHWear
            bodyBuf.put((byte) 0); // engineESWear
            bodyBuf.put((byte) 0); // engineCEWear
            bodyBuf.put((byte) 0); // engineICEWear
            bodyBuf.put((byte) 0); // engineMGUKWear
            bodyBuf.put((byte) 0); // engineTCWear
            bodyBuf.put((byte) 0); // engineBlown
            bodyBuf.put((byte) 0); // engineSeized
        }

        // Combine into one buffer
        ByteBuffer finalBuf = ByteBuffer.allocate(29 + 1012).order(ByteOrder.LITTLE_ENDIAN);
        finalBuf.put(headerBuf.array());
        finalBuf.put(bodyBuf.array());
        finalBuf.flip();

        ByteBuf byteBuf = Unpooled.wrappedBuffer(finalBuf);

        // Deserialize
        PacketHeader header = deserializer.deserializeHeader(byteBuf);
        PacketCarDamageData packet = deserializer.deserializeCarDamage(header, byteBuf);

        // Assertions
        assertNotNull(packet);
        assertEquals(10, packet.getHeader().getPacketId());
        assertEquals(0, packet.getHeader().getPlayerCarIndex());

        CarDamageData playerCar = packet.getCarDamageData()[0];
        assertNotNull(playerCar);
        assertEquals(25, playerCar.getFrontLeftWingDamage());
        assertEquals(35, playerCar.getFrontRightWingDamage());
        assertArrayEquals(new short[]{5, 6, 7, 8}, playerCar.getTyresBlisters());
    }

    @Test
    void testDeserializeSessionHistory() {
        PacketDeserializer deserializer = new PacketDeserializer();

        // 1. Build Header (29 bytes)
        ByteBuffer headerBuf = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN);
        headerBuf.putShort((short) 2025);
        headerBuf.put((byte) 25);
        headerBuf.put((byte) 1);
        headerBuf.put((byte) 23);
        headerBuf.put((byte) 1);
        headerBuf.put((byte) 11); // Session History
        headerBuf.putLong(987654321L);
        headerBuf.putFloat(25.0f);
        headerBuf.putInt(500);
        headerBuf.putInt(500);
        headerBuf.put((byte) 19); // Player car 19
        headerBuf.put((byte) 255);

        // 2. Build Body: 7 + 100*14 + 8*3 = 1431 bytes
        ByteBuffer bodyBuf = ByteBuffer.allocate(1431).order(ByteOrder.LITTLE_ENDIAN);
        bodyBuf.put((byte) 19); // carIdx
        bodyBuf.put((byte) 2);  // numLaps
        bodyBuf.put((byte) 1);  // numTyreStints
        bodyBuf.put((byte) 1);  // bestLapTimeLapNum
        bodyBuf.put((byte) 1);  // bestSector1LapNum
        bodyBuf.put((byte) 1);  // bestSector2LapNum
        bodyBuf.put((byte) 1);  // bestSector3LapNum

        // Lap 1: 73077 ms, S1=21520, S2=30789, S3=20767
        bodyBuf.putInt(73077);
        bodyBuf.putShort((short) 21520);
        bodyBuf.put((byte) 0);
        bodyBuf.putShort((short) 30789);
        bodyBuf.put((byte) 0);
        bodyBuf.putShort((short) 20767);
        bodyBuf.put((byte) 0);
        bodyBuf.put((byte) 15);

        // Remaining 99 laps
        for (int i = 1; i < 100; i++) {
            bodyBuf.putInt(0);
            bodyBuf.putShort((short) 0);
            bodyBuf.put((byte) 0);
            bodyBuf.putShort((short) 0);
            bodyBuf.put((byte) 0);
            bodyBuf.putShort((short) 0);
            bodyBuf.put((byte) 0);
            bodyBuf.put((byte) 0);
        }

        // 8 tyre stints (24 bytes)
        for (int i = 0; i < 8; i++) {
            bodyBuf.put((byte) 0);
            bodyBuf.put((byte) 0);
            bodyBuf.put((byte) 0);
        }

        ByteBuffer finalBuf = ByteBuffer.allocate(29 + 1431).order(ByteOrder.LITTLE_ENDIAN);
        finalBuf.put(headerBuf.array());
        finalBuf.put(bodyBuf.array());
        finalBuf.flip();

        ByteBuf byteBuf = Unpooled.wrappedBuffer(finalBuf);
        PacketHeader header = deserializer.deserializeHeader(byteBuf);
        com.f1telemetry.packets.PacketSessionHistoryData packet = deserializer.deserializeSessionHistory(header, byteBuf);

        assertNotNull(packet);
        assertEquals(11, packet.getHeader().getPacketId());
        assertEquals(19, packet.getCarIdx());
        assertEquals(2, packet.getNumLaps());
        assertEquals(1, packet.getBestLapTimeLapNum());
        assertEquals(73077, packet.getLapHistoryData()[0].getLapTimeInMS());
        assertEquals(21520, packet.getLapHistoryData()[0].getSector1TimeInMS());
        assertEquals(30789, packet.getLapHistoryData()[0].getSector2TimeInMS());
        assertEquals(20767, packet.getLapHistoryData()[0].getSector3TimeInMS());
    }

    @Test
    void testDeserializeRealParticipantsPacket() throws Exception {
        String hex = java.nio.file.Files.lines(java.nio.file.Paths.get("../f1_session_raw.jsonl"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    int pIdx = s.indexOf("\"p\": \"");
                    if (pIdx != -1) {
                        int endIdx = s.indexOf("\"", pIdx + 6);
                        return s.substring(pIdx + 6, endIdx);
                    }
                    return "";
                })
                .filter(p -> p.length() > 14 && p.charAt(12) == '0' && p.charAt(13) == '4')
                .findFirst()
                .orElseThrow();

        byte[] raw = new byte[hex.length() / 2];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }

        PacketParser parser = new PacketParser();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(raw);
        Object parsed = parser.parse(byteBuf);

        assertNotNull(parsed);
        assertTrue(parsed instanceof com.f1telemetry.packets.PacketParticipantsData);
        com.f1telemetry.packets.PacketParticipantsData participants = (com.f1telemetry.packets.PacketParticipantsData) parsed;

        System.out.println("DEBUG PARTICIPANTS: numActive=" + participants.getNumActiveCars());
        for (int i = 0; i < 22; i++) {
            com.f1telemetry.packets.ParticipantData pd = participants.getParticipants()[i];
            System.out.println("Car " + i + ": name=[" + pd.getName() + "], teamId=" + pd.getTeamId() + ", ai=" + pd.getAiControlled());
        }

        assertEquals("GASLY", participants.getParticipants()[0].getName());
        assertEquals("HAMILTON", participants.getParticipants()[19].getName());
    }
}

