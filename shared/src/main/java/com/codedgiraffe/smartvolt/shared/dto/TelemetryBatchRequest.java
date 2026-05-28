package com.codedgiraffe.smartvolt.shared.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Batch of telemetry readings sent from the edge service to the cloud.
 * Each reading includes the validated sensor data plus quality flags.
 */
@Data
public class TelemetryBatchRequest {
    private String deviceId;
    private String edgeApiKey;
    private List<Reading> readings;

    @Data
    public static class Reading {
        private Instant timestamp;
        private Double wattage;
        private Double voltage;
        private Double amperage;
        private Double totalKwh;
        private List<String> qualityFlags;
    }
}
