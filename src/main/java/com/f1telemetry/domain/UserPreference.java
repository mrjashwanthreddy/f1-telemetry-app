package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_preferences")
@Data
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    private float tireOverheatTemp = 100.0f;
    private float brakeOverheatTemp = 1000.0f;
    private float criticalFuelDelta = 0.5f;
    private float lowBatteryPercentage = 10.0f;

    // Phase 10: Customizable voice query hotkey (default Scroll Lock = keycode 70)
    private Integer voiceHotkey = 70;
    private String voiceHotkeyLabel = "Scroll Lock";

    // AI Toggle settings (defaults to false)
    private Boolean aiEnabled = false;

    private String selectedTextModel = "gemini-3.1-flash-lite";
    private String ttsServiceType = "LOCAL"; // "LOCAL", "GOOGLE_CLOUD_TTS"
    private String selectedTtsVoice = "en-GB-Neural2-B";
    private Double creditBalance = 0.00;
    private Double accumulatedCharges = 0.00;

    public boolean isAiEnabled() {
        return aiEnabled != null ? aiEnabled : false;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    // UDP ingestion configurations
    private String udpHost = "127.0.0.1";
    private Integer udpPort = 20777;

    public String getUdpHost() {
        return udpHost != null && !udpHost.isBlank() ? udpHost : "127.0.0.1";
    }

    public int getUdpPort() {
        return udpPort != null ? udpPort : 20777;
    }

    public int getVoiceHotkey() {
        return voiceHotkey != null ? voiceHotkey : 70;
    }

    public String getVoiceHotkeyLabel() {
        return voiceHotkeyLabel != null ? voiceHotkeyLabel : "Scroll Lock";
    }

    public UserPreference(User user) {
        this.user = user;
    }
}
