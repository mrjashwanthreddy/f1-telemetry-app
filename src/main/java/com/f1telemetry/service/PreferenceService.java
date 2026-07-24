package com.f1telemetry.service;

import com.f1telemetry.network.UdpServer;
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
    private final UdpServer udpServer;

    public UserPreference getPreferences(String username) {
        // Try Cache First
        UserPreference cached = preferencesCache.getIfPresent(username);
        if (cached != null) {
            log.debug("Preferences cache HIT for user '{}'", username);
            return cached;
        }

        log.debug("Preferences cache MISS for user '{}' — loading from DB", username);

        // Fetch from DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Preferences lookup failed — user '{}' not found in DB", username);
                    return new RuntimeException("User not found");
                });

        UserPreference pref = preferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    log.info("Creating default preferences for new user '{}'", username);
                    UserPreference newPref = new UserPreference(user);
                    return preferenceRepository.save(newPref);
                });

        // Load into cache
        preferencesCache.put(username, pref);
        log.info("Loaded preferences for '{}' into Caffeine cache", username);
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
        pref.setVoiceHotkey(updateReq.getVoiceHotkey());
        pref.setVoiceHotkeyLabel(updateReq.getVoiceHotkeyLabel());
        if (updateReq.isAiEnabled()) {
            double balance = pref.getCreditBalance() != null ? pref.getCreditBalance() : 0.0;
            if (balance < 5.00) {
                pref.setAiEnabled(false);
            } else {
                pref.setAiEnabled(true);
            }
        } else {
            pref.setAiEnabled(false);
        }
        pref.setUdpHost(updateReq.getUdpHost());
        pref.setUdpPort(updateReq.getUdpPort());
        pref.setSelectedTextModel(updateReq.getSelectedTextModel());
        pref.setTtsServiceType(updateReq.getTtsServiceType());
        pref.setSelectedTtsVoice(updateReq.getSelectedTtsVoice());

        UserPreference saved = preferenceRepository.save(pref);

        // Update cache instantly
        preferencesCache.put(username, saved);
        log.info("Updated preferences for '{}' in DB and cache (aiEnabled={}, udpHost={}, udpPort={})",
                username, saved.isAiEnabled(), saved.getUdpHost(), saved.getUdpPort());

        // Dynamically restart UDP Server with new configurations
        try {
            udpServer.restart(saved.getUdpHost(), saved.getUdpPort());
        } catch (Exception e) {
            log.error("Failed to restart UDP server dynamically after preference change", e);
        }
        return saved;
    }

    public void evictCache(String username) {
        preferencesCache.invalidate(username);
        log.info("Evicted preferences cache for '{}'", username);
    }
}
