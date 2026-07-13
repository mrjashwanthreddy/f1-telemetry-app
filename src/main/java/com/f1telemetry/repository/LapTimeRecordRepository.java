package com.f1telemetry.repository;

import com.f1telemetry.domain.LapTimeRecord;
import com.f1telemetry.domain.RaceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LapTimeRecordRepository extends JpaRepository<LapTimeRecord, Long> {
    List<LapTimeRecord> findByRaceSessionOrderByLapNumberAsc(RaceSession raceSession);

    @org.springframework.transaction.annotation.Transactional
    void deleteByRaceSession(RaceSession raceSession);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(l.lapNumber) FROM LapTimeRecord l WHERE l.raceSession = :raceSession")
    Integer findMaxLapNumberByRaceSession(@org.springframework.data.repository.query.Param("raceSession") RaceSession raceSession);

    boolean existsByRaceSessionAndLapNumber(RaceSession raceSession, int lapNumber);
}
