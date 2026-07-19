package com.f1telemetry.repository;

import com.f1telemetry.domain.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, Long> {
    List<TelemetryRecord> findBySessionIdOrderByTimestampAsc(String sessionId);
    List<TelemetryRecord> findBySessionIdAndCurrentLapNumOrderByTimestampAsc(String sessionId, int currentLapNum);

    // Bulk DELETE — single SQL statement, avoids Hibernate entity-by-entity deletion
    @Transactional
    @Modifying
    @Query("DELETE FROM TelemetryRecord t WHERE t.sessionId = :sessionId")
    int bulkDeleteBySessionId(@Param("sessionId") String sessionId);

    @Transactional
    @Modifying
    @Query("DELETE FROM TelemetryRecord t WHERE t.currentLapNum <= 0 OR (t.speed = 0 AND t.engineRPM = 0 AND t.throttle = 0 AND t.brake = 0 AND t.steer = 0 AND t.gForceLateral = 0 AND t.lapDistance = 0)")
    int deleteEmptyOrInvalidRecords();
}

