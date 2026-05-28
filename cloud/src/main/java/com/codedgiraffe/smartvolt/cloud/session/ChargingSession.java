package com.codedgiraffe.smartvolt.cloud.session;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "charging_sessions",
       indexes = @Index(name = "idx_sessions_device_status",
                        columnList = "deviceId, status"))
public class ChargingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    private Double startKwh;
    private Double endKwh;
    private Double energyUsedKwh;
    private Double estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
}
