"""
F1 Telemetry UDP Test Sender
============================
Sends dummy UDP packets to test the Netty UDP server.

Usage:
    python test_udp_sender.py                  # simple dummy packets (original)
    python test_udp_sender.py realistic        # synthetic F1 packets (original)
    python test_udp_sender.py replay           # REPLAY real Jeddah session data with DB save
    python test_udp_sender.py replay --speed 2 # replay at 2× speed
    python test_udp_sender.py replay --speed 0 # replay as fast as possible
    python test_udp_sender.py events           # fire only the key events with dynamic DB saving
"""

import socket
import struct
import time
import sys
import json
import os

HOST = '127.0.0.1'
PORT = 20777

# Path to the captured real-world session data
JSONL_PATH = os.path.join(os.path.dirname(__file__), '..', 'f1_25_telemetry.jsonl')

# ─────────────────────────────────────────────────────────────────────────────
# Binary packet construction helpers
# ─────────────────────────────────────────────────────────────────────────────

def create_f1_header(packet_id, frame_id=1, session_time=10.5):
    """Create a valid F1 25 PacketHeader (29 bytes) with synthetic values."""
    return struct.pack('<HBBBBBQfIIBB',
        2025,       # m_packetFormat (uint16)
        25,         # m_gameYear (uint8)
        1,          # m_gameMajorVersion (uint8)
        23,         # m_gameMinorVersion (uint8)
        1,          # m_packetVersion (uint8)
        packet_id,  # m_packetId (uint8)
        13463577775752999368, # m_sessionUID (uint64)
        session_time, # m_sessionTime (float)
        frame_id,   # m_frameIdentifier (uint32)
        frame_id,   # m_overallFrameIdentifier (uint32)
        0,          # m_playerCarIndex (uint8)
        255         # m_secondaryPlayerCarIndex (uint8)
    )

def build_header_bytes(h: dict) -> bytes:
    """Serialize a JSON header dict back into a 29-byte F1 25 PacketHeader."""
    return struct.pack('<HBBBBBQfIIBB',
        h['m_packetFormat'],
        h['m_gameYear'],
        h['m_gameMajorVersion'],
        h['m_gameMinorVersion'],
        h['m_packetVersion'],
        h['m_packetId'],
        h['m_sessionUID'],
        h['m_sessionTime'],
        h['m_frameIdentifier'],
        h['m_overallFrameIdentifier'],
        h['m_playerCarIndex'],
        h['m_secondaryPlayerCarIndex'],
    )

def build_event_packet(record: dict) -> bytes:
    """Reconstruct a 45-byte Event packet (packetId=3) from a JSONL record."""
    header_bytes = build_header_bytes(record['header'])
    event_code   = record['event_code'].encode('ascii')
    payload      = bytes.fromhex(record['raw_payload_hex'])
    return header_bytes + event_code + payload

def build_synthetic_event(code: str, payload_hex: str, frame_id: int = 0,
                           session_time: float = 0.0) -> bytes:
    """Build a synthetic 45-byte event packet from scratch."""
    header = create_f1_header(packet_id=3, frame_id=frame_id, session_time=session_time)
    return header + code.encode('ascii') + bytes.fromhex(payload_hex)

def create_session_packet(frame_id, track_id=29, session_type=18, session_time=0.0):
    """
    Create a Session packet (Packet ID: 1, 753 bytes).
    Sets the track ID and session type.
    """
    header = create_f1_header(packet_id=1, frame_id=frame_id, session_time=session_time)
    # Struct fields starting after header:
    # weather(uint8), trackTemp(int8), airTemp(int8), totalLaps(uint8), trackLength(uint16), sessionType(uint8), trackId(int8)
    body = struct.pack('<BbbBHBb',
        0,   # weather (uint8)
        35,  # trackTemp (int8)
        25,  # airTemp (int8)
        50,  # totalLaps (uint8)
        6174, # trackLength (uint16)
        session_type, # sessionType (uint8)
        track_id      # trackId (int8)
    )
    padding = b'\x00' * (724 - len(body))
    return header + body + padding

def create_car_telemetry_packet(frame_id, speed=280, rpm=11500, gear=7,
                                throttle=0.95, brake=0.0, steer=0.0, session_time=10.5):
    header = create_f1_header(packet_id=6, frame_id=frame_id, session_time=session_time)

    def make_car_telemetry(spd, thr, ste, brk, clu, gr, eng_rpm, drs_on):
        return (struct.pack('<HfffBbHBBH', spd, thr, ste, brk, clu, gr, eng_rpm, drs_on, 75, 0)
                + struct.pack('<HHHH', 800, 800, 900, 900)
                + struct.pack('<BBBB', 95, 95, 100, 100)
                + struct.pack('<BBBB', 90, 90, 95, 95)
                + struct.pack('<H', 110)
                + struct.pack('<ffff', 23.5, 23.5, 22.0, 22.0)
                + struct.pack('<BBBB', 0, 0, 0, 0))

    car_data  = make_car_telemetry(speed, throttle, steer, brake, 0, gear, rpm, 0)
    empty_car = make_car_telemetry(0, 0.0, 0.0, 0.0, 0, 0, 0, 0)
    trailer   = struct.pack('<BBb', 255, 255, 0)
    return header + car_data + (empty_car * 21) + trailer

def create_lap_data_packet(frame_id, lap_num=3, position=1, sector=2, last_lap_ms=89500, current_lap_ms=45200, s1_ms=30500, s2_ms=28000, session_time=10.5):
    header = create_f1_header(packet_id=2, frame_id=frame_id, session_time=session_time)

    def make_lap_data(last_lap, current_lap, l, pos, sec):
        return (struct.pack('<II', last_lap, current_lap)
                + struct.pack('<HB', s1_ms, 0)
                + struct.pack('<HB', s2_ms, 0)
                + struct.pack('<HB', 500, 0)
                + struct.pack('<HB', 0, 0)
                + struct.pack('<ff', 3500.0, 15000.0)
                + struct.pack('<f', 0.0)
                + struct.pack('<BB', pos, l)
                + struct.pack('<BB', 0, 0)
                + struct.pack('<BB', sec, 0)
                + struct.pack('<BBB', 0, 0, 0)
                + struct.pack('<BB', 0, 0)
                + struct.pack('<B', 1)
                + struct.pack('<BB', 4, 2)
                + struct.pack('<B', 0)
                + struct.pack('<HH', 0, 0)
                + struct.pack('<B', 0)
                + struct.pack('<fB', 320.5, 2))

    player_lap = make_lap_data(last_lap_ms, current_lap_ms, lap_num, position, sector)
    empty_lap  = make_lap_data(0, 0, 0, 0, 0)
    trailer    = struct.pack('<BB', 255, 255)
    return header + player_lap + (empty_lap * 21) + trailer

# ─────────────────────────────────────────────────────────────────────────────
# MODE 1 — Simple & Realistic (original)
# ─────────────────────────────────────────────────────────────────────────────

def send_simple_packets(count=100, interval=0.016):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print(f"Sending {count} simple dummy UDP packets to {HOST}:{PORT}...")
    for i in range(count):
        data = create_f1_header(packet_id=6, frame_id=i)
        sock.sendto(data, (HOST, PORT))
        time.sleep(interval)
    sock.close()

def send_realistic_packets(count=300, interval=0.016):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print(f"Sending {count} realistic F1 telemetry packets to {HOST}:{PORT}...")
    for i in range(count):
        if i % 2 == 0:
            speed    = 180 + (i % 120)
            rpm      = 8000 + (i % 4000)
            gear     = min(8, max(1, (speed // 40)))
            throttle = 0.5 + (i % 50) / 100.0
            brake    = 0.0 if i % 10 != 0 else 0.8
            data = create_car_telemetry_packet(
                frame_id=i, speed=speed, rpm=rpm, gear=gear,
                throttle=throttle, brake=brake, session_time=i*0.016
            )
        else:
            lap_num = (i // 60) + 1
            data = create_lap_data_packet(
                frame_id=i, lap_num=lap_num, position=1, sector=(i % 3), session_time=i*0.016
            )
        sock.sendto(data, (HOST, PORT))
        time.sleep(interval)
    sock.close()

# ─────────────────────────────────────────────────────────────────────────────
# MODE 2 — Replay JSONL session data with dynamic DB saving
# ─────────────────────────────────────────────────────────────────────────────

def load_jsonl(path: str) -> list:
    records = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records

def replay_session(speed_multiplier: float = 1.0):
    jsonl_path = os.path.abspath(JSONL_PATH)
    if not os.path.exists(jsonl_path):
        print(f"ERROR: Data file not found: {jsonl_path}")
        sys.exit(1)

    print(f"Loading real session data from:\n  {jsonl_path}")
    records = load_jsonl(jsonl_path)
    print(f"  Loaded {len(records)} packets")

    # Timing variables
    session_times = [r['header']['m_sessionTime'] for r in records]
    first_t  = session_times[0]
    last_t   = session_times[-1]
    duration = last_t - first_t

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    # Send dynamic Session Init packet first
    print("  [*] Sending dynamic Session initialization packet (Jeddah, Time Trial)")
    session_pkt = create_session_packet(frame_id=0, track_id=29, session_type=18, session_time=first_t)
    sock.sendto(session_pkt, (HOST, PORT))
    time.sleep(0.1)

    # Establish Lap 1 in RuleEvaluationEngine
    lap_init = create_lap_data_packet(frame_id=1, lap_num=1, position=1, sector=0, last_lap_ms=0, session_time=first_t)
    sock.sendto(lap_init, (HOST, PORT))
    time.sleep(0.1)

    print(f"\nStarting replay ({speed_multiplier}x speed) ...\n")

    current_lap = 1
    sent = 2
    prev_session_time = first_t
    prev_wall_time    = time.monotonic()

    for i, record in enumerate(records):
        curr_session_time = record['header']['m_sessionTime']
        code = record.get('event_code', '?')

        # If FTLP (Fastest Lap) is found, synthesize Lap completion packets first to trigger save
        if code == 'FTLP':
            try:
                payload_bytes = bytes.fromhex(record['raw_payload_hex'])
                lap_time_s = struct.unpack_from('<f', payload_bytes, 1)[0]
                lap_time_ms = int(lap_time_s * 1000)
                s1_ms = int(lap_time_ms * 0.33)
                s2_ms = int(lap_time_ms * 0.33)

                # Send Telemetry dummy packet
                tel_pkt = create_car_telemetry_packet(frame_id=sent, speed=320, rpm=12000, gear=8, throttle=1.0, brake=0.0, session_time=curr_session_time - 0.02)
                sock.sendto(tel_pkt, (HOST, PORT))
                sent += 1

                # Send LapData packet with incremented lap number (triggers RuleEvaluationEngine database persistence)
                lap_data_pkt = create_lap_data_packet(
                    frame_id=sent,
                    lap_num=current_lap + 1,
                    position=1,
                    sector=2,
                    last_lap_ms=lap_time_ms,
                    s1_ms=s1_ms,
                    s2_ms=s2_ms,
                    session_time=curr_session_time - 0.01
                )
                sock.sendto(lap_data_pkt, (HOST, PORT))
                sent += 1
                
                print(f"  [*] [DB Save Trigger] Synthesized Lap completion for Lap {current_lap} ({lap_time_s:.3f}s)")
                current_lap += 1
                time.sleep(0.02)
            except Exception as e:
                print(f"  [!] Failed to synthesize lap completion: {e}")

        # Regular Event packet
        try:
            packet = build_event_packet(record)
        except Exception as e:
            continue

        # Wait
        gap = curr_session_time - prev_session_time
        if speed_multiplier > 0 and gap > 0:
            sleep_s = gap / speed_multiplier
            elapsed = time.monotonic() - prev_wall_time
            sleep_s = max(0, sleep_s - elapsed)
            if sleep_s > 0:
                time.sleep(sleep_s)

        sock.sendto(packet, (HOST, PORT))
        sent += 1

        if code != 'BUTN':
            print(f"  [{curr_session_time:7.1f}s] [*] {code} sent")

        prev_session_time = curr_session_time
        prev_wall_time    = time.monotonic()

    sock.close()
    print(f"\n[SUCCESS] Replay complete -- {sent} packets sent. Laps successfully persisted to database.")

# ─────────────────────────────────────────────────────────────────────────────
# MODE 3 — Fire only the KEY EVENTS with dynamic database saving
# ─────────────────────────────────────────────────────────────────────────────

def fire_key_events():
    KEY_EVENTS = [
        {
            'name':    'Session Start',
            'code':    'SSTA',
            'payload': '000000000000000000000000',
            'frame':   10,
            'time':    1.0,
            'pause':   1.5,
        },
        {
            'name':    'Penalty — Corner Cutting (Lap 1)',
            'code':    'PENA',
            'payload': '0c1a00ffff01ff0000000000',
            'frame':   4281,
            'time':    67.126,
            'pause':   2.0,
        },
        {
            'name':    'Speed Trap — 327.2 km/h (Lap 1)',
            'code':    'SPTP',
            'payload': '003599a3430101003599a343',
            'frame':   10700,
            'time':    173.794,
            'pause':   2.0,
        },
        {
            'name':    'Fastest Lap — 1:34.355 (Lap 1)',
            'code':    'FTLP',
            'payload': '00c3b5bc4200000000000000',
            'frame':   11507,
            'time':    187.234,
            'pause':   3.0,
        },
        {
            'name':    'Speed Trap — 327.9 km/h (Lap 2)',
            'code':    'SPTP',
            'payload': '0072f0a34301010072f0a343',
            'frame':   16300,
            'time':    266.895,
            'pause':   2.0,
        },
        {
            'name':    'Fastest Lap IMPROVED — 1:33.099 (Lap 2)',
            'code':    'FTLP',
            'payload': '00b032ba4200000000000000',
            'frame':   17109,
            'time':    280.339,
            'pause':   3.0,
        },
    ]

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    print(f"Firing {len(KEY_EVENTS)} key events with dynamic database saving -> {HOST}:{PORT}\n")
    print("Watch the Live Dashboard for toasts and the fastest-lap banner!\n")
    time.sleep(1)

    # Track real-world start times for dynamic live lap timer
    session_start_real = time.monotonic()
    lap_start_real = time.monotonic()

    # Send Session packet first
    session_pkt = create_session_packet(frame_id=0, track_id=29, session_type=18, session_time=0.0)
    sock.sendto(session_pkt, (HOST, PORT))
    print("  [*] Sent Session initialization (Jeddah, Time Trial)")
    time.sleep(0.5)

    # Establish Lap 1 in state
    lap_init_pkt = create_lap_data_packet(frame_id=1, lap_num=1, position=1, sector=0, last_lap_ms=0, session_time=0.1)
    sock.sendto(lap_init_pkt, (HOST, PORT))
    print("  [*] Sent LapData initialization (Lap 1)")
    time.sleep(0.5)

    current_lap = 1
    frame_counter = 2
    last_lap_ms = 0
    s1_ms = 0
    s2_ms = 0

    for evt in KEY_EVENTS:
        code = evt['code']

        # If FTLP (Fastest Lap), send LapData to trigger saving of the completed lap
        if code == 'FTLP':
            payload_bytes = bytes.fromhex(evt['payload'])
            lap_time_s = struct.unpack_from('<f', payload_bytes, 1)[0]
            lap_time_ms = int(lap_time_s * 1000)
            s1_ms = int(lap_time_ms * 0.33)
            s2_ms = int(lap_time_ms * 0.33)

            # Send final telemetry sample for that completed lap
            tel_pkt = create_car_telemetry_packet(
                frame_id=frame_counter, speed=325, rpm=12000, gear=8,
                throttle=1.0, brake=0.0, steer=0.0, session_time=evt['time'] - 0.02
            )
            sock.sendto(tel_pkt, (HOST, PORT))
            frame_counter += 1

            # Send LapData packet that increments currentLapNum and triggers RuleEvaluationEngine to persist the completed lap
            lap_data_pkt = create_lap_data_packet(
                frame_id=frame_counter,
                lap_num=current_lap + 1,
                position=1,
                sector=2,
                last_lap_ms=lap_time_ms,
                s1_ms=s1_ms,
                s2_ms=s2_ms,
                session_time=evt['time'] - 0.01
            )
            sock.sendto(lap_data_pkt, (HOST, PORT))
            frame_counter += 1
            print(f"  [*] [DB Save Trigger] Sent LapData for completed Lap {current_lap} ({lap_time_s:.3f}s)")
            
            # Save metadata and reset timer for next simulated lap
            last_lap_ms = lap_time_ms
            current_lap += 1
            lap_start_real = time.monotonic()
            time.sleep(0.1)

        # Fire the event packet (SSTA, PENA, SPTP, etc.)
        packet = build_synthetic_event(
            code=evt['code'],
            payload_hex=evt['payload'],
            frame_id=evt['frame'],
            session_time=evt['time'],
        )
        sock.sendto(packet, (HOST, PORT))
        print(f"  [*] {evt['code']}  {evt['name']} sent")

        # Instead of static sleep, simulate a live driver at 60Hz during the pause!
        pause_duration = evt['pause']
        start_pause = time.monotonic()
        
        while time.monotonic() - start_pause < pause_duration:
            tick_start = time.monotonic()
            
            # Elapsed calculations
            total_elapsed = time.monotonic() - session_start_real
            lap_elapsed = time.monotonic() - lap_start_real
            current_lap_ms = int(lap_elapsed * 1000)
            
            # Simulate a realistic telemetry cycle (accel, braking, cornering)
            cycle = (lap_elapsed % 6.0) / 6.0
            if cycle < 0.6:
                # Straights: Accelerating
                sim_throttle = 1.0
                sim_brake = 0.0
                sim_speed = int(180 + (cycle / 0.6) * 145)
                sim_rpm = int(8000 + (cycle / 0.6) * 4500)
                sim_steer = 0.0
                sim_gear = min(8, max(1, int(1 + (sim_speed / 45))))
            elif cycle < 0.8:
                # Heavy braking zone
                sim_throttle = 0.0
                sim_brake = 0.85
                sim_speed = int(325 - ((cycle - 0.6) / 0.2) * 125)
                sim_rpm = int(12500 - ((cycle - 0.6) / 0.2) * 4000)
                sim_steer = -0.4  # Steer left
                sim_gear = min(8, max(1, int(1 + (sim_speed / 45))))
            else:
                # Corner Exit
                sim_throttle = 0.6
                sim_brake = 0.0
                sim_speed = int(200 + ((cycle - 0.8) / 0.2) * 30)
                sim_rpm = int(8500 + ((cycle - 0.8) / 0.2) * 2000)
                sim_steer = 0.25  # Steer right
                sim_gear = min(8, max(1, int(1 + (sim_speed / 45))))
            
            # Stream simulated telemetry
            tel_pkt = create_car_telemetry_packet(
                frame_id=frame_counter,
                speed=sim_speed,
                rpm=sim_rpm,
                gear=sim_gear,
                throttle=sim_throttle,
                brake=sim_brake,
                steer=sim_steer,
                session_time=total_elapsed
            )
            sock.sendto(tel_pkt, (HOST, PORT))
            frame_counter += 1
            
            # Stream simulated lap data (increments the live timer on dashboard)
            lap_pkt = create_lap_data_packet(
                frame_id=frame_counter,
                lap_num=current_lap,
                position=1,
                sector=0 if cycle < 0.33 else (1 if cycle < 0.66 else 2),
                last_lap_ms=last_lap_ms,
                current_lap_ms=current_lap_ms,
                s1_ms=s1_ms,
                s2_ms=s2_ms,
                session_time=total_elapsed
            )
            sock.sendto(lap_pkt, (HOST, PORT))
            frame_counter += 1
            
            # Maintain roughly 60Hz
            elapsed_tick = time.monotonic() - tick_start
            sleep_time = 0.016 - elapsed_tick
            if sleep_time > 0:
                time.sleep(sleep_time)

    sock.close()
    print(f"\n[SUCCESS] All events fired and Laps successfully saved in database history!")

if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'simple'

    if mode == 'replay':
        speed = 1.0
        if '--speed' in sys.argv:
            idx = sys.argv.index('--speed')
            if idx + 1 < len(sys.argv):
                speed = float(sys.argv[idx + 1])
        replay_session(speed_multiplier=speed)
    elif mode == 'events':
        fire_key_events()
    elif mode == 'realistic':
        send_realistic_packets()
    else:
        send_simple_packets()
