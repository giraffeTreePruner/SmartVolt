package com.codedgiraffe.smartvolt.cloud.model;

import jakarta.persistence.*;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity @Table(name = "devices")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String deviceId;

    // SHA-256 hash of the raw key
    @Column(nullable = false)
    private String deviceKeyHash;

    private Boolean online;

    private String name;
    private String location;

    @Column(updatable = false)
    private Instant firstRegisteredAt;

    @PrePersist
    void onCreate() {
        this.firstRegisteredAt = Instant.now();
        this.online = false;
    }
}
