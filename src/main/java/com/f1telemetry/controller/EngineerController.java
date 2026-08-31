package com.f1telemetry.controller;

import com.f1telemetry.domain.Role;
import com.f1telemetry.domain.User;
import com.f1telemetry.repository.UserRepository;
import com.f1telemetry.service.ActiveUserService;
import com.f1telemetry.state.LiveSessionState;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/engineer")
@RequiredArgsConstructor
public class EngineerController {

    private final UserRepository userRepository;
    private final ActiveUserService activeUserService;
    private final LiveSessionState liveSessionState;

    @GetMapping("/drivers")
    public ResponseEntity<List<DriverSummaryDto>> getAvailableDrivers() {
        List<User> drivers = userRepository.findByRole(Role.ROLE_DRIVER);
        User activeUser = activeUserService.getActiveUser();
        long now = System.currentTimeMillis();
        boolean isDataLive = (now - liveSessionState.getLastUpdateTime()) < 5000;

        List<DriverSummaryDto> dtoList = new ArrayList<>();
        for (User driver : drivers) {
            boolean isActive = activeUser != null && activeUser.getId().equals(driver.getId());
            boolean isLive = isActive && isDataLive;

            String currentLap = "—";
            String position = "P—";
            if (isLive && liveSessionState.getCars() != null && liveSessionState.getPlayerCarIndex() < liveSessionState.getCars().length) {
                var playerCar = liveSessionState.getCars()[liveSessionState.getPlayerCarIndex()];
                if (playerCar != null) {
                    currentLap = String.valueOf(playerCar.getCurrentLapNum());
                    position = "P" + playerCar.getPosition();
                }
            }

            dtoList.add(new DriverSummaryDto(
                    driver.getId(),
                    driver.getUsername(),
                    driver.getTeamPin(),
                    isLive,
                    isActive,
                    currentLap,
                    position,
                    liveSessionState.getTrackId()
            ));
        }

        return ResponseEntity.ok(dtoList);
    }

    @PostMapping("/pair")
    public ResponseEntity<?> pairWithDriver(@RequestBody PairRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String engineerUsername = auth.getName();
        User engineer = userRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer == null) {
            return ResponseEntity.status(404).body("Engineer not found");
        }

        User targetDriver = null;
        if (request.getTeamPin() != null && !request.getTeamPin().trim().isEmpty()) {
            targetDriver = userRepository.findByTeamPin(request.getTeamPin().trim().toUpperCase()).orElse(null);
        } else if (request.getDriverUsername() != null && !request.getDriverUsername().trim().isEmpty()) {
            targetDriver = userRepository.findByUsername(request.getDriverUsername().trim()).orElse(null);
        } else if (request.getDriverId() != null) {
            targetDriver = userRepository.findById(request.getDriverId()).orElse(null);
        }

        if (targetDriver == null) {
            return ResponseEntity.badRequest().body("Driver not found with provided credentials.");
        }

        engineer.setAssignedDriverId(targetDriver.getId());
        userRepository.save(engineer);
        log.info("Engineer '{}' successfully paired with Driver '{}' (ID: {})",
                engineer.getUsername(), targetDriver.getUsername(), targetDriver.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Successfully linked to driver " + targetDriver.getUsername(),
                "driverId", targetDriver.getId(),
                "driverUsername", targetDriver.getUsername(),
                "teamPin", targetDriver.getTeamPin() != null ? targetDriver.getTeamPin() : ""
        ));
    }

    @PostMapping("/unpair")
    public ResponseEntity<?> unpairDriver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String engineerUsername = auth.getName();
        User engineer = userRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer != null) {
            engineer.setAssignedDriverId(null);
            userRepository.save(engineer);
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
        User engineer = userRepository.findByUsername(engineerUsername).orElse(null);
        if (engineer == null || engineer.getAssignedDriverId() == null) {
            return ResponseEntity.ok(Map.of("paired", false));
        }

        User driver = userRepository.findById(engineer.getAssignedDriverId()).orElse(null);
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
    private Long driverId;
    private String driverUsername;
    private String teamPin;
}

@Data
class DriverSummaryDto {
    private final Long id;
    private final String username;
    private final String teamPin;
    private final boolean isLive;
    private final boolean isActive;
    private final String currentLap;
    private final String position;
    private final byte trackId;
}
