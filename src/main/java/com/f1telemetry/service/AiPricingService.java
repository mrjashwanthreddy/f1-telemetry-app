package com.f1telemetry.service;

import com.f1telemetry.domain.User;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiPricingService {

    private final UserPreferenceRepository preferenceRepository;

    // Rates per 1,000,000 units in USD
    private static final Map<String, Double> INPUT_TOKEN_RATES = Map.ofEntries(
        Map.entry("gemini-3.5-pro", 1.25),
        Map.entry("gemini-3.5-flash", 0.075),
        Map.entry("gemini-3.1-flash-lite", 0.0375),
        Map.entry("gemini-2.5-flash", 0.075),
        Map.entry("gemini-2.5-flash-lite", 0.075),
        Map.entry("gemini-2.0-flash", 0.075),
        Map.entry("gemini-1.5-flash", 0.075),
        Map.entry("gemini-1.5-flash-8b", 0.0375),
        Map.entry("gemini-2.5-pro", 1.25),
        Map.entry("gemini-1.5-pro", 1.25)
    );

    private static final Map<String, Double> OUTPUT_TOKEN_RATES = Map.ofEntries(
        Map.entry("gemini-3.5-pro", 5.00),
        Map.entry("gemini-3.5-flash", 0.30),
        Map.entry("gemini-3.1-flash-lite", 0.15),
        Map.entry("gemini-2.5-flash", 0.30),
        Map.entry("gemini-2.5-flash-lite", 0.30),
        Map.entry("gemini-2.0-flash", 0.30),
        Map.entry("gemini-1.5-flash", 0.30),
        Map.entry("gemini-1.5-flash-8b", 0.15),
        Map.entry("gemini-2.5-pro", 5.00),
        Map.entry("gemini-1.5-pro", 5.00)
    );

    // Google Cloud TTS: $16.00 per 1,000,000 characters for Neural Voices
    private static final double GOOGLE_TTS_CHARACTER_RATE = 16.00 / 1_000_000.0; 

    public double calculateTextCost(String modelName, int inputTokens, int outputTokens) {
        double inputRate = INPUT_TOKEN_RATES.getOrDefault(modelName, 0.075) / 1_000_000.0;
        double outputRate = OUTPUT_TOKEN_RATES.getOrDefault(modelName, 0.30) / 1_000_000.0;
        return (inputTokens * inputRate) + (outputTokens * outputRate);
    }

    public double calculateTtsCost(String ttsServiceType, int characters) {
        if ("GOOGLE_CLOUD_TTS".equalsIgnoreCase(ttsServiceType)) {
            return characters * GOOGLE_TTS_CHARACTER_RATE;
        }
        return 0.0; // Local browser TTS is free
    }

    public void accrueCharges(User user, double amount) {
        UserPreference pref = preferenceRepository.findByUser(user).orElse(null);
        if (pref != null) {
            double currentAccrued = pref.getAccumulatedCharges() != null ? pref.getAccumulatedCharges() : 0.0;
            pref.setAccumulatedCharges(currentAccrued + amount);
            preferenceRepository.save(pref);
        }
    }

    public boolean payAccruedCharges(User user) {
        UserPreference pref = preferenceRepository.findByUser(user).orElse(null);
        if (pref == null) return false;

        double accrued = pref.getAccumulatedCharges() != null ? pref.getAccumulatedCharges() : 0.0;
        double currentBal = pref.getCreditBalance() != null ? pref.getCreditBalance() : 0.0;

        if (currentBal < accrued) {
            return false; // Insufficient credits in wallet
        }
        pref.setCreditBalance(currentBal - accrued);
        pref.setAccumulatedCharges(0.00);
        preferenceRepository.save(pref);
        return true;
    }
    
    public void addCredits(User user, double amount) {
        UserPreference pref = preferenceRepository.findByUser(user).orElse(null);
        if (pref != null) {
            double currentBal = pref.getCreditBalance() != null ? pref.getCreditBalance() : 0.0;
            pref.setCreditBalance(currentBal + amount);
            preferenceRepository.save(pref);
        }
    }
}
