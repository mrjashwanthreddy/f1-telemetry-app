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

    public boolean isAiEnabled() {
        return aiEnabled != null ? aiEnabled : false;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
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
