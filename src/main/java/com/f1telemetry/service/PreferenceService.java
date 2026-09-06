package com.f1telemetry.service;

import com.f1telemetry.network.UdpServer;
import com.github.benmanes.caffeine.cache.Cache;
import com.f1telemetry.domain.RaceEngineer;
import com.f1telemetry.domain.SimDriver;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.repository.RaceEngineerRepository;
import com.f1telemetry.repository.SimDriverRepository;
import com.f1telemetry.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final SimDriverRepository simDriverRepository;
    private final RaceEngineerRepository raceEngineerRepository;
    private final Cache<String, UserPreference> preferencesCache;
    private final UdpServer udpServer;

    public UserPreference getPreferences(String username) {
        // Try Cache First
        UserPreference cached = preferencesCache.getIfPresent(username);
        if (cached != null) {
            log.trace("Preferences cache HIT for user '{}'", username);
            return cached;
        }

        log.debug("Preferences cache MISS for user '{}' — loading from DB", username);

        // Fetch driver first
        Optional<SimDriver> driverOpt = simDriverRepository.findByUsername(username);
        if (driverOpt.isPresent()) {
            SimDriver driver = driverOpt.get();
            UserPreference pref = preferenceRepository.findByDriver(driver)
                    .orElseGet(() -> {
                        log.info("Creating default preferences for new sim driver '{}'", username);
                        UserPreference newPref = new UserPreference(driver);
                        return preferenceRepository.save(newPref);
                    });

            preferencesCache.put(username, pref);
            log.info("Loaded preferences for driver '{}' into Caffeine cache", username);
            return pref;
        }

        // If not driver, check if engineer has an assigned driver
        Optional<RaceEngineer> engineerOpt = raceEngineerRepository.findByUsername(username);
        if (engineerOpt.isPresent() && engineerOpt.get().getAssignedDriverId() != null) {
            Optional<SimDriver> pairedDriverOpt = simDriverRepository.findById(engineerOpt.get().getAssignedDriverId());
            if (pairedDriverOpt.isPresent()) {
                SimDriver pairedDriver = pairedDriverOpt.get();
                UserPreference pref = preferenceRepository.findByDriver(pairedDriver)
                        .orElseGet(() -> preferenceRepository.save(new UserPreference(pairedDriver)));
                return pref;
            }
        }

        // Return a transient default preference if user is an unlinked engineer or guest
        UserPreference transientPref = new UserPreference();
        return transientPref;
    }

    public UserPreference updatePreferences(String username, UserPreference updateReq) {
        SimDriver driver = simDriverRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Sim Driver not found: " + username));

        UserPreference pref = preferenceRepository.findByDriver(driver)
                .orElse(new UserPreference(driver));

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
