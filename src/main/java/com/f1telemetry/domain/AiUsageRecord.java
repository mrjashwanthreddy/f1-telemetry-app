package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_usage_records")
@Data
@NoArgsConstructor
public class AiUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private long timestamp;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String feature; // "lap-alert", "session-debrief", "chat"

    @Column(name = "input_units", nullable = false)
    private int inputUnits;

    @Column(name = "output_units", nullable = false)
    private int outputUnits;

    @Column(name = "unit_type", nullable = false)
    private String unitType; // "TOKENS", "CHARACTERS"

    @Column(name = "cost_usd", nullable = false, columnDefinition = "NUMERIC(10,6)")
    private double costUsd;

    public AiUsageRecord(User user, long timestamp, String modelName, String feature, int inputUnits, int outputUnits, String unitType, double costUsd) {
        this.user = user;
        this.timestamp = timestamp;
        this.modelName = modelName;
        this.feature = feature;
        this.inputUnits = inputUnits;
        this.outputUnits = outputUnits;
        this.unitType = unitType;
        this.costUsd = costUsd;
    }
}
