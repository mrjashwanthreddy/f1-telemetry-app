package com.f1telemetry.service;

import com.f1telemetry.domain.TelemetryRecord;
import com.f1telemetry.repository.TelemetryRecordRepository;
import com.f1telemetry.state.CarState;
import com.f1telemetry.state.LiveSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncPersistenceService {

    private final TelemetryRecordRepository telemetryRecordRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    
    // Unbounded queue for buffering frames. In production, might cap at 10,000 to prevent OOM
    private final LinkedBlockingQueue<TelemetryRecord> writeQueue = new LinkedBlockingQueue<>();

    private int frameCounter = 0;

    /**
     * Called directly by the 60Hz UDP thread to enqueue a frame.
     * Non-blocking O(1) operation.
     */
    public void enqueueTelemetryFrame(LiveSessionState state) {
        frameCounter++;
        // We only queue 1 frame per 3 (downsample to 10Hz for DB) to save space
        if (frameCounter % 3 != 0) {
            return;
        }

        int playerIdx = state.getPlayerCarIndex();
        CarState playerCar = state.getCars()[playerIdx];

        // Skip enqueuing if it's not a proper lap or the telemetry is empty (e.g. in garage/menu/paused)
        if (playerCar.getCurrentLapNum() <= 0 || isEmptyTelemetry(playerCar)) {
            return;
        }
        
        String dbSessionId = com.f1telemetry.engine.RuleEvaluationEngine.getDbSessionId(state);
        int offsetLapNum = playerCar.getCurrentLapNum() + state.getLapOffset();

        TelemetryRecord record = new TelemetryRecord(
            dbSessionId,
            offsetLapNum,
            System.currentTimeMillis(),
            playerCar.getSpeed(),
            playerCar.getThrottle(),
            playerCar.getBrake(),
            playerCar.getEngineRPM(),
            playerCar.getLapDistance(),    // Phase 10: corner zone detection
            playerCar.getSteer(),          // Phase 10: steering input
            playerCar.getGForceLateral()   // Phase 10: lateral G-force
        );
        
        writeQueue.offer(record);
    }

    private boolean isEmptyTelemetry(CarState playerCar) {
        return playerCar.getSpeed() == 0 
            && playerCar.getEngineRPM() == 0 
            && playerCar.getThrottle() == 0.0f 
            && playerCar.getBrake() == 0.0f 
            && playerCar.getSteer() == 0.0f 
            && playerCar.getGForceLateral() == 0.0f 
            && playerCar.getLapDistance() == 0.0f;
    }

    /**
     * Runs every 5 seconds on a background thread.
     * Drains the queue and saves in bulk.
     */
    @Async("persistenceTaskExecutor")
    @Scheduled(fixedRate = 5000)
    public void flushQueueToDatabase() {
        if (writeQueue.isEmpty()) return;

        List<TelemetryRecord> batch = new ArrayList<>();
        writeQueue.drainTo(batch);
        int remainingInQueue = writeQueue.size();

        if (!batch.isEmpty()) {
            try {
                long startMs = System.currentTimeMillis();
                String sql = "INSERT INTO telemetry_records (session_id, current_lap_num, timestamp, speed, throttle, brake, engine_rpm, lap_distance, steer, g_force_lateral) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        TelemetryRecord r = batch.get(i);
                        ps.setString(1, r.getSessionId());
                        ps.setInt(2, r.getCurrentLapNum());
                        ps.setLong(3, r.getTimestamp());
                        ps.setInt(4, r.getSpeed());
                        ps.setFloat(5, r.getThrottle());
                        ps.setFloat(6, r.getBrake());
                        ps.setInt(7, r.getEngineRPM());
                        ps.setFloat(8, r.getLapDistance());
                        ps.setFloat(9, r.getSteer());
                        ps.setFloat(10, r.getGForceLateral());
                    }

                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });
                long elapsed = System.currentTimeMillis() - startMs;
                log.info("Batch inserted {} telemetry records to DB in {}ms (queue remaining: {})", 
                        batch.size(), elapsed, remainingInQueue);
            } catch (Exception e) {
                log.error("Failed to batch insert telemetry records via JdbcTemplate, falling back to JPA", e);
                try {
                    telemetryRecordRepository.saveAll(batch);
                    log.info("Fallback: Batch inserted {} telemetry records to DB using JPA", batch.size());
                } catch (Exception ex) {
                    log.error("Fallback JPA save also failed", ex);
                }
            }
        }
    }
}
