package com.codedgiraffe.smartvolt.model;

/*
This is the JPA entity representing a registered device in the system.
*/

import jakarta.persistence.*;
import java.time.Instant;

// USING LOMBOK FOR GETTERS/SETTERS/CONSTRUCTORS
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

@Entity @Table(name = "devices")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Unique MQTT topic per device, i.e. sonoff-01
    @Column(unique = true, nullable = false)
    private String deviceId;

    // SHA-256 has of the raw key 
    @Column(nullable = false)
    private String deviceKeyHash;

    private Boolean online;

    // Optional metadata
    private String name;
    private String location;

    // Permanent first registration timestamp
    @Column(updatable = false)
    private Instant firstRegisteredAt;

    // Initialize
    @PrePersist
    void onCreate() {
        this.firstRegisteredAt = Instant.now();
        this.online = false;
    }
}
