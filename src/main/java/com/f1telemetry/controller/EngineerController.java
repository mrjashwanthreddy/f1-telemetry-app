package com.f1telemetry.controller;

import com.f1telemetry.domain.RaceEngineer;
import com.f1telemetry.domain.SimDriver;
import com.f1telemetry.repository.RaceEngineerRepository;
import com.f1telemetry.repository.SimDriverRepository;
import com.f1telemetry.state.LiveSessionState;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/engineer")
@RequiredArgsConstructor
public class EngineerController {

    private final RaceEngineerRepository raceEngineerRepository;
    private final SimDriverRepository simDriverRepository;
    private final LiveSessionState liveSessionState;

    @PostMapping("/pair")
    public ResponseEntity<?> pairWithDriver(@RequestBody PairRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String engineerUsername = auth.getName();
        RaceEngineer engineer = raceEngineerRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer == null) {
            return ResponseEntity.status(404).body("Race Engineer profile not found.");
        }

        if (request.getTeamPin() == null || request.getTeamPin().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Team PIN is required to establish telemetry link.");
        }

        String pin = request.getTeamPin().trim().toUpperCase();
        Optional<SimDriver> targetDriverOpt = simDriverRepository.findByTeamPin(pin);
        if (targetDriverOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("No Sim Driver found matching Team PIN '" + pin + "'.");
        }

        SimDriver targetDriver = targetDriverOpt.get();
        engineer.setAssignedDriverId(targetDriver.getId());
        raceEngineerRepository.save(engineer);
        log.info("Race Engineer '{}' successfully linked to Sim Driver '{}' (ID: {}, PIN: {})",
                engineer.getUsername(), targetDriver.getUsername(), targetDriver.getId(), targetDriver.getTeamPin());

        return ResponseEntity.ok(Map.of(
                "message", "Successfully linked to driver " + targetDriver.getUsername(),
                "driverId", targetDriver.getId(),
                "driverUsername", targetDriver.getUsername(),
                "teamPin", targetDriver.getTeamPin()
        ));
    }

    @PostMapping("/unpair")
    public ResponseEntity<?> unpairDriver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String engineerUsername = auth.getName();
        RaceEngineer engineer = raceEngineerRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer != null) {
            engineer.setAssignedDriverId(null);
            raceEngineerRepository.save(engineer);
            log.info("Race Engineer '{}' disconnected from driver.", engineerUsername);
        }

        return ResponseEntity.ok(Map.of("message", "Unpaired from driver."));
    }

    @GetMapping("/paired-driver")
    public ResponseEntity<?> getPairedDriver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String engineerUsername = auth.getName();
        RaceEngineer engineer = raceEngineerRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer == null || engineer.getAssignedDriverId() == null) {
            return ResponseEntity.ok(Map.of("paired", false));
        }

        SimDriver driver = simDriverRepository.findById(engineer.getAssignedDriverId()).orElse(null);
        if (driver == null) {
            return ResponseEntity.ok(Map.of("paired", false));
        }

        return ResponseEntity.ok(Map.of(
                "paired", true,
                "driverId", driver.getId(),
                "driverUsername", driver.getUsername(),
                "teamPin", driver.getTeamPin() != null ? driver.getTeamPin() : ""
        ));
    }

    @GetMapping("/live-state")
    public ResponseEntity<LiveSessionState> getLiveSnapshot() {
        return ResponseEntity.ok(liveSessionState);
    }
}

@Data
class PairRequest {
    private String teamPin;
}
