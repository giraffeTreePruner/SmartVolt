package com.codedgiraffe.smartvolt.shared.validation;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryPayload;

/**
 * Stateless validator for telemetry readings. Checks physical ranges,
 * null fields, and cross-reading anomalies (spikes, kWh rollback).
 *
 * Rules:
 *   wattage:  0 <= w <= 2000
 *   voltage:  100 <= v <= 135
 *   amperage: 0 <= a <= 20
 */
public class DataQualityValidator {

    private static final double MAX_WATTAGE = 2000.0;
    private static final double MIN_VOLTAGE = 100.0;
    private static final double MAX_VOLTAGE = 135.0;
    private static final double MAX_AMPERAGE = 20.0;
    private static final double SPIKE_THRESHOLD = 500.0;

    /**
     * Validate a reading against physical ranges and optionally against
     * a previous reading for cross-reading checks.
     *
     * @param current  the current telemetry reading
     * @param previous the previous reading (nullable for the first reading)
     * @return quality result with any flags raised
     */
    public QualityResult validate(TelemetryPayload current, TelemetryPayload previous) {
        QualityResult result = new QualityResult();

        // Null field checks
        if (current.getWattage() == null || current.getVoltage() == null
                || current.getAmperage() == null) {
            result.addFlag(QualityFlag.MISSING_FIELD);
            return result; // Can't do range checks with nulls
        }

        // Range checks
        if (current.getWattage() < 0 || current.getWattage() > MAX_WATTAGE) {
            result.addFlag(QualityFlag.OUT_OF_RANGE);
        }
        if (current.getVoltage() < MIN_VOLTAGE || current.getVoltage() > MAX_VOLTAGE) {
            result.addFlag(QualityFlag.VOLTAGE_ANOMALY);
        }
        if (current.getAmperage() < 0 || current.getAmperage() > MAX_AMPERAGE) {
            result.addFlag(QualityFlag.CURRENT_ANOMALY);
        }

        // Cross-reading checks (only when previous is available)
        if (previous != null) {
            // kWh rollback
            if (current.getTotalKwh() != null && previous.getTotalKwh() != null
                    && current.getTotalKwh() < previous.getTotalKwh()) {
                result.addFlag(QualityFlag.KWH_ROLLBACK);
            }

            // Wattage spike
            if (previous.getWattage() != null
                    && Math.abs(current.getWattage() - previous.getWattage()) > SPIKE_THRESHOLD) {
                result.addFlag(QualityFlag.SPIKE_DETECTED);
            }
        }

        return result;
    }
}
