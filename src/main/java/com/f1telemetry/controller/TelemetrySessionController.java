package com.f1telemetry.controller;

import com.f1telemetry.domain.User;
import com.f1telemetry.repository.UserRepository;
import com.f1telemetry.service.ActiveUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class TelemetrySessionController {

    private final ActiveUserService activeUserService;
    private final UserRepository userRepository;

    @PostMapping("/start")
    public ResponseEntity<String> startTelemetrySession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String username = auth.getName();
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            activeUserService.setActiveUser(user);
            log.info("User {} is now the Active Player. UDP Telemetry will be attributed to this user.", username);
        }, () -> log.warn("User {} not found in database", username));

        return ResponseEntity.ok("Session started successfully");
    }
}
