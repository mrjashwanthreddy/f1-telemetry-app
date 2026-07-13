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

        if (!batch.isEmpty()) {
            telemetryRecordRepository.saveAll(batch);
            log.info("Batch inserted {} telemetry records to DB", batch.size());
        }
    }
}
