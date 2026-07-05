"""
F1 Telemetry UDP Test Sender
Sends dummy UDP packets to test the Netty UDP server.

Usage: python test_udp_sender.py

For Task 1.3: Sends simple dummy packets to verify UDP reception.
For Task 1.4: Sends realistic F1 25 formatted packets with valid headers.
"""

import socket
import struct
import time
import sys

HOST = '127.0.0.1'
PORT = 20777

def create_f1_header(packet_id, frame_id=1):
    """
    Create a valid F1 25 PacketHeader (29 bytes).
    
    Layout:
        uint16  packetFormat        = 2025
        uint8   gameYear            = 25
        uint8   gameMajorVersion    = 1
        uint8   gameMinorVersion    = 0
        uint8   packetVersion       = 1
        uint8   packetId            = packet_id
        uint64  sessionUID          = 12345678
        float   sessionTime         = 10.5
        uint32  frameIdentifier     = frame_id
        uint32  overallFrameId      = frame_id
        uint8   playerCarIndex      = 0
        uint8   secondaryPlayerIdx  = 255
    """
    return struct.pack('<HBBBBBQfIIBB',
        2025,           # packetFormat (uint16)
        25,             # gameYear (uint8)
        1,              # gameMajorVersion (uint8)
        0,              # gameMinorVersion (uint8)
        1,              # packetVersion (uint8)
        packet_id,      # packetId (uint8)
        12345678,       # sessionUID (uint64)
        10.5,           # sessionTime (float)
        frame_id,       # frameIdentifier (uint32)
        frame_id,       # overallFrameIdentifier (uint32)
        0,              # playerCarIndex (uint8)
        255             # secondaryPlayerCarIndex (uint8)
    )

def create_car_telemetry_packet(frame_id, speed=280, rpm=11500, gear=7, throttle=0.95, brake=0.0):
    """
    Create a Car Telemetry packet (Packet ID: 6).
    Only fills the player car (index 0) with meaningful data.
    """
    header = create_f1_header(packet_id=6, frame_id=frame_id)
    
    # Actually, let's build the full 60-byte per-car struct properly
    # uint16 speed, float throttle, float steer, float brake, 
    # uint8 clutch, int8 gear, uint16 rpm, uint8 drs, uint8 revLightsPercent,
    # uint16 revLightsBitValue, uint16[4] brakesTemp, uint8[4] tyresSurfaceTemp,
    # uint8[4] tyresInnerTemp, uint16 engineTemp, float[4] tyresPressure, uint8[4] surfaceType
    
    def make_car_telemetry(spd, thr, ste, brk, clu, gr, eng_rpm, drs_on):
        return struct.pack('<HfffBbHBBH',
            spd, thr, ste, brk, clu, gr, eng_rpm, drs_on, 75, 0
        ) + struct.pack('<HHHH', 800, 800, 900, 900     # brakesTemperature[4]
        ) + struct.pack('<BBBB', 95, 95, 100, 100        # tyresSurfaceTemperature[4]
        ) + struct.pack('<BBBB', 90, 90, 95, 95          # tyresInnerTemperature[4]
        ) + struct.pack('<H', 110                         # engineTemperature
        ) + struct.pack('<ffff', 23.5, 23.5, 22.0, 22.0  # tyresPressure[4]
        ) + struct.pack('<BBBB', 0, 0, 0, 0               # surfaceType[4]
        )
    
    # Build 22 cars: player car has real data, rest are zeroed
    car_data = make_car_telemetry(speed, throttle, 0.0, brake, 0, gear, rpm, 0)
    empty_car = make_car_telemetry(0, 0.0, 0.0, 0.0, 0, 0, 0, 0)
    
    all_cars = car_data + (empty_car * 21)
    
    # Trailing fields: mfdPanelIndex, mfdPanelIndexSecondary, suggestedGear
    trailer = struct.pack('<BBb', 255, 255, 0)
    
    return header + all_cars + trailer

def create_lap_data_packet(frame_id, lap_num=3, position=1, sector=2):
    """
    Create a Lap Data packet (Packet ID: 2).
    Only fills the player car (index 0) with meaningful data.
    """
    header = create_f1_header(packet_id=2, frame_id=frame_id)
    
    # LapData struct (57 bytes per car)
    def make_lap_data(last_lap_ms, current_lap_ms, lap, pos, sec):
        return struct.pack('<II',
            last_lap_ms,        # lastLapTimeInMS (uint32)
            current_lap_ms,     # currentLapTimeInMS (uint32)
        ) + struct.pack('<HB', 30500, 0     # sector1TimeMSPart, sector1TimeMinutesPart
        ) + struct.pack('<HB', 28000, 0     # sector2TimeMSPart, sector2TimeMinutesPart
        ) + struct.pack('<HB', 500, 0       # deltaToCarInFrontMSPart, deltaToCarInFrontMinutesPart
        ) + struct.pack('<HB', 0, 0         # deltaToRaceLeaderMSPart, deltaToRaceLeaderMinutesPart
        ) + struct.pack('<ff', 3500.0, 15000.0  # lapDistance, totalDistance
        ) + struct.pack('<f', 0.0           # safetyCarDelta
        ) + struct.pack('<BB', pos, lap     # carPosition, currentLapNum
        ) + struct.pack('<BB', 0, 0         # pitStatus, numPitStops
        ) + struct.pack('<BB', sec, 0       # sector, currentLapInvalid
        ) + struct.pack('<BBB', 0, 0, 0     # penalties, totalWarnings, cornerCuttingWarnings
        ) + struct.pack('<BB', 0, 0         # numUnservedDT, numUnservedSG
        ) + struct.pack('<B', 1             # gridPosition
        ) + struct.pack('<BB', 4, 2         # driverStatus (on track), resultStatus (active)
        ) + struct.pack('<B', 0             # pitLaneTimerActive
        ) + struct.pack('<HH', 0, 0         # pitLaneTimeInLaneInMS, pitStopTimerInMS
        ) + struct.pack('<B', 0             # pitStopShouldServePen
        ) + struct.pack('<fB', 320.5, 2     # speedTrapFastestSpeed, speedTrapFastestLap
        )
    
    player_lap = make_lap_data(89500, 45200, lap_num, position, sector)
    empty_lap = make_lap_data(0, 0, 0, 0, 0)
    
    all_laps = player_lap + (empty_lap * 21)
    
    # Trailing: timeTrialPBCarIdx, timeTrialRivalCarIdx
    trailer = struct.pack('<BB', 255, 255)
    
    return header + all_laps + trailer


def send_simple_packets(count=100, interval=0.016):
    """Send simple dummy packets for Task 1.3 verification."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print(f"Sending {count} simple dummy UDP packets to {HOST}:{PORT}...")
    
    for i in range(count):
        # Just send a basic header-only packet
        data = create_f1_header(packet_id=6, frame_id=i)
        sock.sendto(data, (HOST, PORT))
        time.sleep(interval)
        
        if (i + 1) % 60 == 0:
            print(f"  Sent {i + 1} packets...")
    
    sock.close()
    print(f"Done! Sent {count} packets.")


def send_realistic_packets(count=300, interval=0.016):
    """Send realistic F1 25 formatted packets for Task 1.4 verification."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print(f"Sending {count} realistic F1 telemetry packets to {HOST}:{PORT}...")
    
    for i in range(count):
        # Alternate between telemetry and lap data packets
        if i % 2 == 0:
            # Simulate varying speed and RPM
            speed = 180 + (i % 120)
            rpm = 8000 + (i % 4000)
            gear = min(8, max(1, (speed // 40)))
            throttle = 0.5 + (i % 50) / 100.0
            brake = 0.0 if i % 10 != 0 else 0.8
            
            data = create_car_telemetry_packet(
                frame_id=i, speed=speed, rpm=rpm, 
                gear=gear, throttle=throttle, brake=brake
            )
        else:
            lap_num = (i // 60) + 1
            data = create_lap_data_packet(
                frame_id=i, lap_num=lap_num, position=1, sector=(i % 3)
            )
        
        sock.sendto(data, (HOST, PORT))
        time.sleep(interval)
        
        if (i + 1) % 60 == 0:
            print(f"  Sent {i + 1} packets...")
    
    sock.close()
    print(f"Done! Sent {count} realistic packets.")


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'simple'
    
    if mode == 'realistic':
        send_realistic_packets()
    else:
        send_simple_packets()
    
    print("\nTest complete. Check your application logs for received packet messages.")
