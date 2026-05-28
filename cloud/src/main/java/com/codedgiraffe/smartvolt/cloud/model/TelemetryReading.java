package com.codedgiraffe.smartvolt.cloud.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity @Table(name = "telemetry_readings",
         indexes = @Index(name = "idx_device_timestamp", columnList = "deviceId, timestamp")
)
public class TelemetryReading {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String deviceId;

    private Instant timestamp;
    private Double wattage;
    private Double voltage;
    private Double amperage;
    private Double totalKwh;

    @PrePersist
    void onCreate() {
        this.timestamp = Optional.ofNullable(this.timestamp)
                .orElseGet(Instant::now);
    }
}
