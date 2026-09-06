package com.f1telemetry.repository;

import com.f1telemetry.domain.SimDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimDriverRepository extends JpaRepository<SimDriver, Long> {
    Optional<SimDriver> findByUsername(String username);
    Optional<SimDriver> findByTeamPin(String teamPin);
    boolean existsByUsername(String username);
    boolean existsByTeamPin(String teamPin);
}
