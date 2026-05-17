package com.codedgiraffe.smartvolt.model;

/* 
This is the JPA entity representing a telemetry reading from a device. 
Each time a device publishes data to its MQTT topic, a new TelemetryReading 
is created and stored in the database.
*/

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

// USING LOMBOK FOR GETTERS/SETTERS/CONSTRUCTORS
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

    // Sensor data
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
