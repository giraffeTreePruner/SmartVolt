package com.codedgiraffe.smartvolt.shared.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Command sent from cloud to edge for device control.
 */
@Data
public class CommandDto {
    private String id;
    private String deviceId;
    private String commandType;   // POWER_ON, POWER_OFF
    private String payload;
    private Instant expiresAt;
}
