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
}
