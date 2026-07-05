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

    @Column(nullable = false)
    private String trackName;

    @Column(nullable = false)
    private long timestamp;

    public RaceSession(User user, String sessionId, String trackName, long timestamp) {
        this.user = user;
        this.sessionId = sessionId;
        this.trackName = trackName;
        this.timestamp = timestamp;
    }
}
