package com.f1telemetry.controller;

import com.f1telemetry.domain.User;
import com.f1telemetry.repository.UserRepository;
import com.f1telemetry.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("Registration attempt with existing username '{}'", request.getUsername());
            return ResponseEntity.badRequest().body("Username already exists");
        }
        User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        log.info("New user registered: '{}'", request.getUsername());
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                .map(user -> {
                    log.info("Login successful for user '{}'", user.getUsername());
                    return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getUsername())));
                })
                .orElseGet(() -> {
                    log.warn("Login failed for username '{}'", request.getUsername());
                    return ResponseEntity.status(401).build();
                });
    }
}

@Data
class AuthRequest {
    private String username;
    private String password;
}

@Data
class AuthResponse {
    private final String token;
}
