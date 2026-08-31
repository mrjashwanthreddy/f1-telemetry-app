package com.f1telemetry.controller;

import com.f1telemetry.domain.Role;
import com.f1telemetry.domain.User;
import com.f1telemetry.repository.UserRepository;
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

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private static final String PIN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private String generateDriverPin() {
        StringBuilder sb = new StringBuilder("F1-");
        for (int i = 0; i < 4; i++) {
            sb.append(PIN_CHARS.charAt(random.nextInt(PIN_CHARS.length())));
        }
        return sb.toString();
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("Registration attempt with existing username '{}'", request.getUsername());
            return ResponseEntity.badRequest().body("Username already exists");
        }

        Role userRole = Role.ROLE_DRIVER;
        if (request.getRole() != null) {
            String roleStr = request.getRole().trim().toUpperCase();
            if (roleStr.contains("ENGINEER")) {
                userRole = Role.ROLE_ENGINEER;
            } else if (roleStr.contains("ADMIN")) {
                userRole = Role.ROLE_ADMIN;
            }
        }

        User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()), userRole);
        if (userRole == Role.ROLE_DRIVER) {
            user.setTeamPin(generateDriverPin());
        }
        userRepository.save(user);
        log.info("New user registered: '{}' as {}", request.getUsername(), userRole);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                .map(user -> {
                    // Ensure driver has a team pin if missing
                    if (user.getRole() == Role.ROLE_DRIVER && (user.getTeamPin() == null || user.getTeamPin().isEmpty())) {
                        user.setTeamPin(generateDriverPin());
                        userRepository.save(user);
                    }
                    String roleName = user.getRole() != null ? user.getRole().name() : Role.ROLE_DRIVER.name();
                    String token = jwtUtil.generateToken(user.getUsername(), roleName);
                    log.info("Login successful for user '{}' with role {}", user.getUsername(), roleName);
                    return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), roleName, user.getTeamPin(), user.getAssignedDriverId()));
                })
                .orElseGet(() -> {
                    log.warn("Login failed for username '{}'", request.getUsername());
                    return ResponseEntity.status(401).build();
                });
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .map(user -> {
                    String roleName = user.getRole() != null ? user.getRole().name() : Role.ROLE_DRIVER.name();
                    return ResponseEntity.ok(new AuthResponse(null, user.getUsername(), roleName, user.getTeamPin(), user.getAssignedDriverId()));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

@Data
class AuthRequest {
    private String username;
    private String password;
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
