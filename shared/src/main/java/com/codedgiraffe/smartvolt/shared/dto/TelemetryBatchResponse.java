package com.codedgiraffe.smartvolt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cloud response after ingesting a telemetry batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryBatchResponse {
    private int accepted;
    private int rejected;
}
