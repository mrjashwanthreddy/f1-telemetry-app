package com.f1telemetry.repository;

import com.f1telemetry.domain.RaceEngineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RaceEngineerRepository extends JpaRepository<RaceEngineer, Long> {
    Optional<RaceEngineer> findByUsername(String username);
    boolean existsByUsername(String username);
}
