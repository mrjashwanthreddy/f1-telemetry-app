package com.f1telemetry.repository;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LapTimeRecordRepository extends JpaRepository<LapTimeRecord, Long> {
    List<LapTimeRecord> findByRaceSessionOrderByLapNumberAsc(RaceSession raceSession);
}
