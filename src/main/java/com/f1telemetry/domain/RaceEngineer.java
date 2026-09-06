package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "race_engineers")
@Data
@NoArgsConstructor
public class RaceEngineer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_ENGINEER;

    @Column(name = "assigned_driver_id")
    private Long assignedDriverId;

    @Column(name = "created_at")
    private Long createdAt;

    public RaceEngineer(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = Role.ROLE_ENGINEER;
        this.createdAt = System.currentTimeMillis();
    }
}
