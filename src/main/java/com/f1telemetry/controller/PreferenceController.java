package com.f1telemetry.controller;

import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<UserPreference> getMyPreferences(Authentication authentication) {
        String username = authentication.getName();
        log.debug("Fetching preferences for user '{}'", username);
        return ResponseEntity.ok(preferenceService.getPreferences(username));
    }

    @PostMapping
    public ResponseEntity<UserPreference> updateMyPreferences(
            Authentication authentication, 
            @RequestBody UserPreference newPrefs) {
        String username = authentication.getName();
        log.debug("Updating preferences for user '{}'", username);
        return ResponseEntity.ok(preferenceService.updatePreferences(username, newPrefs));
    }
}
