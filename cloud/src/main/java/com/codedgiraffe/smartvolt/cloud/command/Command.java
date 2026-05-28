package com.codedgiraffe.smartvolt.cloud.command;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "commands",
       indexes = @Index(name = "idx_commands_device_status",
                        columnList = "deviceId, status"))
public class Command {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandType commandType;

    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant deliveredAt;
    private Instant acknowledgedAt;
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusSeconds(300);
        }
    }
}
