package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sim_drivers")
@Data
@NoArgsConstructor
public class SimDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_DRIVER;

    @Column(name = "team_pin", unique = true, nullable = false)
    private String teamPin;

    @Column(name = "created_at")
    private Long createdAt;

    public SimDriver(String username, String passwordHash, String teamPin) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = Role.ROLE_DRIVER;
        this.teamPin = teamPin;
        this.createdAt = System.currentTimeMillis();
    }
}
