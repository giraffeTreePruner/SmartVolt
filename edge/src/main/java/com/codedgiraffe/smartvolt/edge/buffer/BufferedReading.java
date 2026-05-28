package com.codedgiraffe.smartvolt.edge.buffer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "buffered_readings")
public class BufferedReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private Instant timestamp;

    private Double wattage;
    private Double voltage;
    private Double amperage;
    private Double totalKwh;

    private String qualityFlags;

    @Column(nullable = false)
    private boolean synced;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.timestamp == null) {
            this.timestamp = this.createdAt;
        }
    }
}
