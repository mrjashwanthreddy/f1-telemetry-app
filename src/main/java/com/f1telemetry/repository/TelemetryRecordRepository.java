package com.f1telemetry.repository;

import com.f1telemetry.domain.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, Long> {
    List<TelemetryRecord> findBySessionIdOrderByTimestampAsc(String sessionId);
    List<TelemetryRecord> findBySessionIdAndCurrentLapNumOrderByTimestampAsc(String sessionId, int currentLapNum);
}
