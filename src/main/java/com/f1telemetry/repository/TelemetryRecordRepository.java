package com.f1telemetry.repository;

import com.f1telemetry.domain.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, Long> {
    List<TelemetryRecord> findBySessionIdOrderByTimestampAsc(String sessionId);
    List<TelemetryRecord> findBySessionIdAndCurrentLapNumOrderByTimestampAsc(String sessionId, int currentLapNum);

    @org.springframework.transaction.annotation.Transactional
    void deleteBySessionId(String sessionId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TelemetryRecord t WHERE t.currentLapNum <= 0 OR (t.speed = 0 AND t.engineRPM = 0 AND t.throttle = 0 AND t.brake = 0 AND t.steer = 0 AND t.gForceLateral = 0 AND t.lapDistance = 0)")
    int deleteEmptyOrInvalidRecords();
}
