package com.f1telemetry.controller;

import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.network.UdpServer;
import com.f1telemetry.repository.UserRepository;
import com.f1telemetry.service.ActiveUserService;
import com.f1telemetry.service.PreferenceService;
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
    private final PreferenceService preferenceService;
    private final UdpServer udpServer;

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
            
            // Fetch this user's preferences and adjust the UDP host/port dynamically.
            // Force 0.0.0.0 if the stored value is the old loopback default (127.0.0.1),
            // which only worked for local installs, not cloud deployments.
            try {
                UserPreference prefs = preferenceService.getPreferences(username);
                String udpHost = prefs.getUdpHost();
                if ("127.0.0.1".equals(udpHost)) {
                    udpHost = "0.0.0.0";
                }
                udpServer.restart(udpHost, prefs.getUdpPort());
            } catch (Exception e) {
                log.error("Failed to load user preferences and restart UDP server on session start", e);
            }
        }, () -> log.warn("User {} not found in database", username));

        return ResponseEntity.ok("Session started successfully");
    }
}
