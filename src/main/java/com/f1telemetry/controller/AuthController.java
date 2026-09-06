package com.f1telemetry.controller;

import com.f1telemetry.domain.RaceEngineer;
import com.f1telemetry.domain.Role;
import com.f1telemetry.domain.SimDriver;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.repository.RaceEngineerRepository;
import com.f1telemetry.repository.SimDriverRepository;
import com.f1telemetry.repository.UserPreferenceRepository;
import com.f1telemetry.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SimDriverRepository simDriverRepository;
    private final RaceEngineerRepository raceEngineerRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private static final String PIN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private String generateDriverPin() {
        while (true) {
            StringBuilder sb = new StringBuilder("F1-");
            for (int i = 0; i < 4; i++) {
                sb.append(PIN_CHARS.charAt(random.nextInt(PIN_CHARS.length())));
            }
            String candidatePin = sb.toString();
            if (!simDriverRepository.existsByTeamPin(candidatePin)) {
                return candidatePin;
            }
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username and password are required.");
        }

        String username = request.getUsername().trim();
        if (simDriverRepository.existsByUsername(username) || raceEngineerRepository.existsByUsername(username)) {
            log.warn("Registration attempt with existing username '{}'", username);
            return ResponseEntity.badRequest().body("Username already exists");
        }

        boolean isEngineer = false;
        if (request.getRole() != null) {
            String roleStr = request.getRole().trim().toUpperCase();
            if (roleStr.contains("ENGINEER")) {
                isEngineer = true;
            }
        }

        if (isEngineer) {
            RaceEngineer engineer = new RaceEngineer(username, passwordEncoder.encode(request.getPassword()));
            raceEngineerRepository.save(engineer);
            log.info("New Race Engineer registered in race_engineers table: '{}'", username);
            return ResponseEntity.ok("Race Engineer registered successfully");
        } else {
            String pin = generateDriverPin();
            SimDriver driver = new SimDriver(username, passwordEncoder.encode(request.getPassword()), pin);
            simDriverRepository.save(driver);
            // Initialize default preferences
            userPreferenceRepository.save(new UserPreference(driver));
            log.info("New Sim Driver registered in sim_drivers table: '{}' with Team PIN '{}'", username, pin);
            return ResponseEntity.ok("Sim Driver registered successfully");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        // 1. Check Sim Driver table
        Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
        if (driverOpt.isPresent()) {
            SimDriver driver = driverOpt.get();
            if (passwordEncoder.matches(password, driver.getPasswordHash())) {
                String token = jwtUtil.generateToken(driver.getUsername(), Role.ROLE_DRIVER.name());
                log.info("Driver login successful: '{}' (PIN: {})", driver.getUsername(), driver.getTeamPin());
                return ResponseEntity.ok(new AuthResponse(token, driver.getUsername(), Role.ROLE_DRIVER.name(), driver.getTeamPin(), null));
            }
        }

        // 2. Check Race Engineer table
        Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
        if (engineerOpt.isPresent()) {
            RaceEngineer engineer = engineerOpt.get();
            if (passwordEncoder.matches(password, engineer.getPasswordHash())) {
                String token = jwtUtil.generateToken(engineer.getUsername(), Role.ROLE_ENGINEER.name());
                log.info("Race Engineer login successful: '{}' (Assigned Driver ID: {})", engineer.getUsername(), engineer.getAssignedDriverId());
                return ResponseEntity.ok(new AuthResponse(token, engineer.getUsername(), Role.ROLE_ENGINEER.name(), null, engineer.getAssignedDriverId()));
            }
        }

        log.warn("Login failed for username '{}'", username);
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getNewPassword() == null || request.getNewPassword().trim().length() < 3) {
            return ResponseEntity.badRequest().body("Valid username and a new password (min 3 characters) are required.");
        }

        String username = request.getUsername().trim();
        String newHash = passwordEncoder.encode(request.getNewPassword().trim());

        String roleStr = request.getRole() != null ? request.getRole().trim().toUpperCase() : "";

        if (roleStr.contains("ENGINEER")) {
            Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
            if (engineerOpt.isPresent()) {
                RaceEngineer engineer = engineerOpt.get();
                engineer.setPasswordHash(newHash);
                raceEngineerRepository.save(engineer);
                log.info("Password successfully reset for Race Engineer '{}'", username);
                return ResponseEntity.ok("Password reset successfully for Race Engineer " + username);
            }
            return ResponseEntity.status(404).body("Race Engineer account with username '" + username + "' not found.");
        } else if (roleStr.contains("DRIVER")) {
            Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
            if (driverOpt.isPresent()) {
                SimDriver driver = driverOpt.get();
                driver.setPasswordHash(newHash);
                simDriverRepository.save(driver);
                log.info("Password successfully reset for Sim Driver '{}'", username);
                return ResponseEntity.ok("Password reset successfully for Sim Driver " + username);
            }
            return ResponseEntity.status(404).body("Sim Driver account with username '" + username + "' not found.");
        } else {
            // Check both tables
            Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
            if (driverOpt.isPresent()) {
                SimDriver driver = driverOpt.get();
                driver.setPasswordHash(newHash);
                simDriverRepository.save(driver);
                log.info("Password successfully reset for Sim Driver '{}'", username);
                return ResponseEntity.ok("Password reset successfully for Sim Driver " + username);
            }

            Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
            if (engineerOpt.isPresent()) {
                RaceEngineer engineer = engineerOpt.get();
                engineer.setPasswordHash(newHash);
                raceEngineerRepository.save(engineer);
                log.info("Password successfully reset for Race Engineer '{}'", username);
                return ResponseEntity.ok("Password reset successfully for Race Engineer " + username);
            }

            return ResponseEntity.status(404).body("Account with username '" + username + "' not found.");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        String username = auth.getName();

        Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
        if (driverOpt.isPresent()) {
            SimDriver driver = driverOpt.get();
            return ResponseEntity.ok(new AuthResponse(null, driver.getUsername(), Role.ROLE_DRIVER.name(), driver.getTeamPin(), null));
        }

        Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
        if (engineerOpt.isPresent()) {
            RaceEngineer engineer = engineerOpt.get();
            return ResponseEntity.ok(new AuthResponse(null, engineer.getUsername(), Role.ROLE_ENGINEER.name(), null, engineer.getAssignedDriverId()));
        }

        return ResponseEntity.notFound().build();
    }
}

@Data
class AuthRequest {
    private String username;
    private String password;
    private String role;
}

@Data
class ResetPasswordRequest {
    private String username;
    private String newPassword;
    private String role;
}

@Data
class AuthResponse {
    private final String token;
    private final String username;
    private final String role;
    private final String teamPin;
    private final Long assignedDriverId;

    public AuthResponse(String token) {
        this(token, null, "ROLE_DRIVER", null, null);
    }

    public AuthResponse(String token, String username, String role, String teamPin, Long assignedDriverId) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.teamPin = teamPin;
        this.assignedDriverId = assignedDriverId;
    }
}
