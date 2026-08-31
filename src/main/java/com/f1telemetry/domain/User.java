package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Role role = Role.ROLE_DRIVER;

    @Column(name = "team_pin")
    private String teamPin;

    @Column(name = "assigned_driver_id")
    private Long assignedDriverId;

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = Role.ROLE_DRIVER;
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Role.ROLE_DRIVER;
    }
}
