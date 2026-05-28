package com.codedgiraffe.smartvolt.shared.validation;

/**
 * Quality flags raised during data validation on the edge.
 */
public enum QualityFlag {
    /** A sensor value is outside its physical range */
    OUT_OF_RANGE,
    /** Voltage outside 100-135V (US residential) */
    VOLTAGE_ANOMALY,
    /** Amperage outside 0-20A (15A outlet with headroom) */
    CURRENT_ANOMALY,
    /** A required field is null */
    MISSING_FIELD,
    /** Gap between consecutive readings exceeds expected interval */
    TIMESTAMP_GAP,
    /** totalKwh decreased — possible meter reset or rollback */
    KWH_ROLLBACK,
    /** Wattage jump > 500W in one interval */
    SPIKE_DETECTED
}
