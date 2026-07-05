# PostgreSQL Database Schema & Hibernate Entities

This document defines the database tables, relations, and Hibernate/JPA schema mapped to the PostgreSQL 15+ persistence database.

---

## 1. Entity Relationship Diagram (ERD)

```
       ┌──────────────────┐
       │      users       │
       ├──────────────────┤
       │ id (PK)          │◄───┐
       │ username (UQ)    │    │ (1-to-1)
       │ password_hash    │    │
       └──────────────────┘    │
                 │             │
                 │ (1-to-many) │
                 ▼             │
       ┌──────────────────┐    │
       │  race_sessions   │    │
       ├──────────────────┤    │
       │ id (PK)          │    │
       │ user_id (FK)     │    │
       │ session_id (UQ)  │    │
       │ track_name       │    │
       │ timestamp        │    │
       └──────────────────┘    │
                 │             │
                 │ (1-to-many) │
                 ▼             │
       ┌──────────────────┐    │   ┌────────────────────┐
       │ lap_time_records │    │   │  user_preferences  │
       ├──────────────────┤    │   ├────────────────────┤
       │ id (PK)          │    └───│ id (PK)            │
       │ session_id (FK)  │        │ user_id (FK, UQ)   │
       │ lap_number       │        │ tire_overheat_temp │
       │ sector1_time_ms  │        │ brake_overheat_temp│
       │ sector2_time_ms  │        │ critical_fuel_delta│
       │ sector3_time_ms  │        │ low_battery_pct    │
       │ total_lap_time_ms│        └────────────────────┘
       └──────────────────┘

       ┌──────────────────┐
       │telemetry_records │ (Unindexed trace list,
       ├──────────────────┤  isolated for high-speed
       │ id (PK)          │  sequential inserts)
       │ session_id       │
       │ current_lap_num  │
       │ timestamp        │
       │ speed            │
       │ throttle         │
       │ brake            │
       │ engine_rpm       │
       └──────────────────┘
```

---

## 2. Table Specifications

### 1. `users`
- **Class**: [User](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/User.java)
- **Table Name**: `users`
- **Columns**:
  - `id`: `BIGINT` (Primary Key, Identity Auto-Increment)
  - `username`: `VARCHAR(255)` (Unique, Not Null)
  - `password_hash`: `VARCHAR(255)` (Not Null)

### 2. `user_preferences`
- **Class**: [UserPreference](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/UserPreference.java)
- **Table Name**: `user_preferences`
- **Columns**:
  - `id`: `BIGINT` (Primary Key, Identity Auto-Increment)
  - `user_id`: `BIGINT` (Foreign Key -> `users.id`, Unique, Not Null)
  - `tire_overheat_temp`: `REAL` (Defaults to `100.0`°C)
  - `brake_overheat_temp`: `REAL` (Defaults to `1000.0`°C)
  - `critical_fuel_delta`: `REAL` (Defaults to `0.5` Laps)
  - `low_battery_percentage`: `REAL` (Defaults to `10.0`%)

### 3. `race_sessions`
- **Class**: [RaceSession](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/RaceSession.java)
- **Table Name**: `race_sessions`
- **Columns**:
  - `id`: `BIGINT` (Primary Key, Identity Auto-Increment)
  - `user_id`: `BIGINT` (Foreign Key -> `users.id`, Not Null)
  - `session_id`: `VARCHAR(255)` (Unique UUID representation, Not Null)
  - `track_name`: `VARCHAR(255)` (Not Null)
  - `timestamp`: `BIGINT` (Epoch millisecond session creation time, Not Null)

### 4. `lap_time_records`
- **Class**: [LapTimeRecord](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/LapTimeRecord.java)
- **Table Name**: `lap_time_records`
- **Columns**:
  - `id`: `BIGINT` (Primary Key, Identity Auto-Increment)
  - `session_id`: `BIGINT` (Foreign Key -> `race_sessions.id`, Not Null)
  - `lap_number`: `INTEGER` (The specific lap index, e.g. 1, 2)
  - `sector1_time_inms`: `INTEGER` (Sector 1 time in MS)
  - `sector2_time_inms`: `INTEGER` (Sector 2 time in MS)
  - `sector3_time_inms`: `INTEGER` (Sector 3 time in MS, computed as `total - S1 - S2`)
  - `total_lap_time_inms`: `INTEGER` (Total lap time in MS)

### 5. `telemetry_records`
- **Class**: [TelemetryRecord](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/TelemetryRecord.java)
- **Table Name**: `telemetry_records`
- **Columns**:
  - `id`: `BIGINT` (Primary Key, Identity Auto-Increment)
  - `session_id`: `VARCHAR(255)` (String identifier of the active session, e.g. UUID)
  - `current_lap_num`: `INTEGER` (Lap index when sample was captured)
  - `timestamp`: `BIGINT` (Epoch millisecond stamp)
  - `speed`: `INTEGER` (Speed in km/h)
  - `throttle`: `REAL` (0.0 to 1.0)
  - `brake`: `REAL` (0.0 to 1.0)
  - `engine_rpm`: `INTEGER` (RPM)

---

## 3. High-Frequency Downsampling Schema

The application handles rapid high-frequency streams by downsampling telemetry inputs to **10Hz** (10 samples per second) before committing them to the database.

- **Storage Impact**: Storing 60Hz traces would result in `~216,000` rows per hour per driver. Downsampling to 10Hz reduces database size by **83.3%** (`~36,000` rows per hour).
- **Execution Threading**: Data is batched in memory using a non-blocking `LinkedBlockingQueue` and committed to Postgres every 5 seconds in blocks using Hibernate's `saveAll()` method, completely isolating the UDP parsing event loop.
