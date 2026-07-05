package com.f1telemetry.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.f1telemetry.domain.User;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.repository.UserPreferenceRepository;
import com.f1telemetry.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final Cache<String, UserPreference> preferencesCache;

    public UserPreference getPreferences(String username) {
        // Try Cache First
        UserPreference cached = preferencesCache.getIfPresent(username);
        if (cached != null) {
            return cached;
        }

        // Fetch from DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserPreference pref = preferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    UserPreference newPref = new UserPreference(user);
                    return preferenceRepository.save(newPref);
                });

        // Load into cache
        preferencesCache.put(username, pref);
        log.info("Loaded preferences for {} into Caffeine cache", username);
        return pref;
    }

    public UserPreference updatePreferences(String username, UserPreference updateReq) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        UserPreference pref = preferenceRepository.findByUser(user)
                .orElse(new UserPreference(user));

        pref.setTireOverheatTemp(updateReq.getTireOverheatTemp());
        pref.setBrakeOverheatTemp(updateReq.getBrakeOverheatTemp());
        pref.setCriticalFuelDelta(updateReq.getCriticalFuelDelta());
        pref.setLowBatteryPercentage(updateReq.getLowBatteryPercentage());

        UserPreference saved = preferenceRepository.save(pref);
        
        // Update cache instantly
        preferencesCache.put(username, saved);
        log.info("Updated preferences for {} in DB and cache", username);
        
        return saved;
    }
}
