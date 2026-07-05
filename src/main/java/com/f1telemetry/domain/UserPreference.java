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

    public UserPreference(User user) {
        this.user = user;
    }
}
