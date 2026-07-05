# Binary Telemetry Packet Specifications (F1 25)

The F1 Game streams telemetry data via UDP using Little-Endian byte ordering. All packet frames are preceded by a universal **29-byte header**, followed by a type-specific payload.

---

## 1. Packet Types (Packet IDs)

| ID | Name | Frequency | Description |
|---|---|---|---|
| **0** | **Motion** | ~60Hz | Physics data for all 22 cars (position, velocity, G-forces, yaw/pitch/roll). |
| **1** | **Session** | 2Hz | General session metadata (weather, air/track temps, marshal zones, AI difficulty). |
| **2** | **Lap Data** | ~60Hz | Lap times, sector progress, safety car intervals, pit count, and penalties. |
| **3** | **Event** | Event-driven | Triggers like fastest lap, retirements, penalties, start lights, collisions. |
| **4** | **Participants** | 0.2Hz | Driver details, active vehicle indexes, names (char[32]), and liveries. |
| **5** | **Car Setups** | 2Hz | Aerodynamic configs, tire pressures, anti-roll bars, and starting fuel load. |
| **6** | **Car Telemetry** | ~60Hz | Live telemetry (speed, inputs, RPM, gear, tyre temps, engine temp). |
| **7** | **Car Status** | ~60Hz | Car settings (ABS, traction control, fuel mix, tire age, ERS store energy). |
| **8** | **Final Classification** | End of session | Race results, total points, total pit stops, and visual tyre compounds. |
| **10**| **Car Damage** | 2Hz | Tyre wear %, structural wing/floor damage, engine wear percentages. |

---

## 2. Universal Packet Header (29 Bytes)

All incoming UDP packets are prefixed with this header layout:

```
┌─────────────────────────────────────────────────────────────┐
│                        PACKET HEADER                        │
├──────────────┬──────────┬───────────┬───────────────────────┤
│ Field        │ Type     │ Size (B)  │ Description           │
├──────────────┼──────────┼───────────┼───────────────────────┤
│ format       │ uint16   │ 2         │ Game format (2025)    │
│ gameYear     │ uint8    │ 1         │ Game Year (25)        │
│ majorVersion │ uint8    │ 1         │ Game Major Version    │
│ minorVersion │ uint8    │ 1         │ Game Minor Version    │
│ packetVersion│ uint8    │ 1         │ Packet version (1)    │
│ packetId     │ uint8    │ 1         │ Identifier (0 to 10)  │
│ sessionUID   │ uint64   │ 8         │ Unique session ID     │
│ sessionTime  │ float    │ 4         │ Session duration (s)  │
│ frameIndex   │ uint32   │ 4         │ In-session frame index│
│ globalFrame  │ uint32   │ 4         │ Total frame counter   │
│ playerIdx    │ uint8    │ 1         │ Player car index (0-21)│
│ secondaryIdx │ uint8    │ 1         │ Sec player index (255)│
└──────────────┴──────────┴───────────┴───────────────────────┘
```

---

## 3. Selected In-Memory Structure Mappings

### Car Telemetry Data (Packet ID: 6)
Each car structure is **60 bytes** long. The packet contains the `Header` + Array of 22 `CarTelemetryData` structs + `mfdPanelIndex` (uint8) + `mfdPanelIndexSecondary` (uint8) + `suggestedGear` (int8).

- **Speed** (`uint16`): Speed in km/h.
- **Throttle** (`float`): 0.0 to 1.0.
- **Steer** (`float`): -1.0 (left) to 1.0 (right).
- **Brake** (`float`): 0.0 to 1.0.
- **Clutch** (`uint8`): Clutch value (0 to 100).
- **Gear** (`int8`): Current gear (1 to 8, N=0, R=-1).
- **EngineRPM** (`uint16`): Engine revolutions per minute.
- **DRS** (`uint8`): DRS active flag (0 = off, 1 = on).
- **BrakesTemperature** (`uint16[4]`): FL, FR, RL, RR temperatures in Celsius.
- **TyresSurfaceTemperature** (`uint8[4]`): FL, FR, RL, RR temperatures.
- **TyresInnerTemperature** (`uint8[4]`): FL, FR, RL, RR temperatures.
- **EngineTemperature** (`uint16`): Engine temperature in Celsius.
- **TyresPressure** (`float[4]`): FL, FR, RL, RR pressures in PSI.
- **SurfaceType** (`uint8[4]`): Ground material surface index.

### Lap Data (Packet ID: 2)
Contains the `Header` + Array of 22 `LapData` structs (each **57 bytes**) + `timeTrialPBCarIdx` (uint8) + `timeTrialRivalCarIdx` (uint8).

- **LastLapTimeInMS** (`uint32`): Previous lap time in milliseconds.
- **CurrentLapTimeInMS** (`uint32`): Active lap time in milliseconds.
- **Sector1TimeMSPart** (`uint16`): Sector 1 milliseconds part.
- **Sector1TimeMinutesPart** (`uint8`): Sector 1 minutes part.
- **Sector2TimeMSPart** (`uint16`): Sector 2 milliseconds part.
- **Sector2TimeMinutesPart** (`uint8`): Sector 2 minutes part.
- **CarPosition** (`uint8`): Dynamic position in the field (1-22).
- **CurrentLapNum** (`uint8`): Current lap index.
- **PitStatus** (`uint8`): 0 = none, 1 = pitting, 2 = in pit lane.
- **Sector** (`uint8`): Active track sector (0 = S1, 1 = S2, 2 = S3).
- **CurrentLapInvalid** (`uint8`): 0 = valid, 1 = invalid (corner cutting).

### Car Damage Data (Packet ID: 10)
Contains the `Header` + Array of 22 `CarDamageData` structs + diagnostic flags.

- **TyresWear** (`float[4]`): FL, FR, RL, RR tire wear percentage (0.0 to 100.0).
- **TyresDamage** (`uint8[4]`): Tire damage percentage.
- **BrakesDamage** (`uint8[4]`): Brake damage percentage.
- **FrontLeftWingDamage** (`uint8`): Structural damage % (0-100).
- **FrontRightWingDamage** (`uint8`): Structural damage % (0-100).
- **RearWingDamage** (`uint8`): Structural damage % (0-100).
- **GearBoxDamage** (`uint8`): Gearbox wear percentage.
- **EngineDamage** (`uint8`): Internal engine damage percentage.

---

## 4. Deserialization Conversions (Netty ByteBuf)

Java's default types are signed. Netty's `ByteBuf` handles unsigned conversion and little-endian ordering:

- **Unsigned 8-bit** (`uint8`): `buf.readUnsignedByte()` -> stores in a Java `short`.
- **Signed 8-bit** (`int8`): `buf.readByte()` -> stores in a Java `byte`.
- **Unsigned 16-bit** (`uint16`): `buf.readUnsignedShortLE()` -> stores in a Java `int`.
- **Signed 16-bit** (`int16`): `buf.readShortLE()` -> stores in a Java `short`.
- **Unsigned 32-bit** (`uint32`): `buf.readUnsignedIntLE()` -> stores in a Java `long`.
- **Float** (`float`): `buf.readFloatLE()` -> stores in a Java `float`.
- **Unsigned 64-bit** (`uint64`): `buf.readLongLE()` -> stores in a Java `long` (requires custom bitmask handling if sign bit is set).
