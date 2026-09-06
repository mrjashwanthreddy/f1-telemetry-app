package com.f1telemetry.repository;

import com.f1telemetry.domain.RaceSession;
import com.f1telemetry.domain.SimDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RaceSessionRepository extends JpaRepository<RaceSession, Long> {
    Optional<RaceSession> findBySessionId(String sessionId);
    List<RaceSession> findByDriverOrderByTimestampDesc(SimDriver driver);
}
