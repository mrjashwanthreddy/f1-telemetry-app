package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "race_sessions")
@Data
@NoArgsConstructor
public class RaceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String sessionId;

    /** Human-readable track name resolved from F1 25 trackId (e.g. "Jeddah", "Silverstone"). */
    @Column(nullable = false)
    private String trackName;

    /** Session mode resolved from F1 25 sessionType (e.g. "Time Trial", "Career Race", "Online"). */
    @Column(nullable = false)
    private String sessionType;

    @Column(nullable = false)
    private long timestamp;

    /** Full constructor used by RuleEvaluationEngine. */
    public RaceSession(User user, String sessionId, String trackName, String sessionType, long timestamp) {
        this.user = user;
        this.sessionId = sessionId;
        this.trackName = trackName;
        this.sessionType = sessionType;
        this.timestamp = timestamp;
    }
}
