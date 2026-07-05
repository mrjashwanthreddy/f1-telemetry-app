# System Architecture & Processing Pipeline

This document explains the real-time processing architecture of the F1 Telemetry Application.

---

## 1. High-Level Ingestion and Storage Flow

The application handles extreme frequency updates by dividing paths into a **High-Speed Critical Path** (real-time streaming/alerts) and an **Asynchronous Storage Path** (deferred batch writing).

```
                        [ F1 Game (UDP Client) ]
                                   │
                                   │ (Raw Binary UDP Packets up to 60Hz)
                                   ▼
                       [ 1. Netty UDP Ingestion ]
                                   │
                                   ▼
                      [ 2. Binary Buffer Parser ]
                                   │
                                   ▼
                       [ 3. Live State Cache ]
                                   │
           ┌────────────────────────┴────────────────────────┐
           │ (Critical Path: Fast In-Memory)                 │ (Asynchronous Path: Deferred)
           ▼                                                 ▼
  [ 4. Rule Evaluation Engine ]                    [ 6. Async Batch Queue ]
           │                                                 │
           ▼ (Alert Triggered)                               ▼ (Micro-batches @ 10Hz)
  [ 5. WebSocket Message Broker ]                  [ 7. PostgreSQL Database ]
           │                                                 │
           ▼ (Push Event < 100ms)                            ▼
  [ Live Dashboard Dashboard UI ]                  [ Post-Race Analysis UI ]
```

---

## 2. Ingestion Pipeline Details

### 1. Ingestion Loop (Netty)
- Class: [UdpServer](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/network/UdpServer.java) and [UdpPacketHandler](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/network/UdpPacketHandler.java)
- A Netty event loop group listens on local UDP port `20777`.
- Incoming `DatagramPacket` frames are read as Netty `ByteBuf` payloads.
- Bytes are routed to the parser layer, then immediately released back to the allocator.

### 2. Binary Buffer Parser
- Class: [PacketParser](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/parser/PacketParser.java) and [PacketDeserializer](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/parser/PacketDeserializer.java)
- Unpacks little-endian C-struct fields into Java domain packet definitions.
- Inspects the `PacketHeader` to extract the `packetId` and routes the buffer to the corresponding deserialization subroutine.

### 3. Live State Cache
- Class: [LiveSessionState](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/state/LiveSessionState.java) and [CarState](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/state/CarState.java)
- An in-memory, thread-safe, pre-allocated cache that acts as a snapshot of the current session.
- The 22 car states are updated dynamically whenever new Telemetry, Lap, Status, or Damage packets arrive.

---

## 3. High-Speed Critical Path

### 4. Rule Evaluation Engine
- Class: [RuleEvaluationEngine](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/engine/RuleEvaluationEngine.java)
- Monitored values (e.g. tyre surface temperature) are evaluated against active driver preferences.
- Interrogates [PreferenceService](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/service/PreferenceService.java) which pulls configurations from a Caffeine in-memory cache to avoid database query overhead.
- Detects lap completions by monitoring current lap indicators and saves lap durations (Sector 1, 2, and 3) to the database.

### 5. WebSocket Message Broker
- Class: [WebSocketConfig](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/config/WebSocketConfig.java)
- Broadcaster Class: [TelemetryBroadcastService](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/service/TelemetryBroadcastService.java)
- A scheduler task queries the live cache and runs the evaluation engine at exactly 33ms intervals (~30Hz).
- Active telemetry state is streamed to `/topic/live-telemetry`.
- Generated alert objects are pushed immediately to `/topic/live-alerts`.
- Decouples the 60Hz ingestion frequency from UI rendering to protect the browser DOM from freezing.

---

## 4. Asynchronous Storage Path

### 6. Downsampling & Batch In-Memory Queue
- Class: [AsyncPersistenceService](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/service/AsyncPersistenceService.java)
- To conserve disk space, incoming telemetry events are downsampled: only **1 out of every 3 frames** (10Hz resolution) is saved.
- Telemetry details (speed, throttle, brake, engine RPM) are packed into [TelemetryRecord](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/domain/TelemetryRecord.java) instances and enqueued into a thread-safe `LinkedBlockingQueue`.

### 7. Thread-Isolated Disk Flusher
- Scheduled every 5 seconds, `flushQueueToDatabase()` runs on a dedicated thread pool thread named `DB-Writer-X` (configured in [AsyncConfig](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/config/AsyncConfig.java)).
- Drains the write queue completely and runs `saveAll()` to perform a Postgres bulk-insert.
- Ensures Postgres disk writing never disrupts the UDP packet listener.
