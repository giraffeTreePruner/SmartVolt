package com.codedgiraffe.smartvolt.shared.validation;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityValidatorTest {

    private DataQualityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DataQualityValidator();
    }

    @Test
    void validReading_noFlags() {
        TelemetryPayload reading = buildPayload(1140.0, 121.0, 9.4, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFlags()).isEmpty();
    }

    @Test
    void nullWattage_flagsMissingField() {
        TelemetryPayload reading = buildPayload(null, 121.0, 9.4, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasFlag(QualityFlag.MISSING_FIELD)).isTrue();
    }

    @Test
    void wattageOutOfRange_flagsOutOfRange() {
        TelemetryPayload reading = buildPayload(3000.0, 121.0, 9.4, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.isValid()).isTrue(); // Out of range is a warning, not invalid
        assertThat(result.hasFlag(QualityFlag.OUT_OF_RANGE)).isTrue();
    }

    @Test
    void voltageAnomaly_lowVoltage() {
        TelemetryPayload reading = buildPayload(1140.0, 90.0, 9.4, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.hasFlag(QualityFlag.VOLTAGE_ANOMALY)).isTrue();
    }

    @Test
    void voltageAnomaly_highVoltage() {
        TelemetryPayload reading = buildPayload(1140.0, 140.0, 9.4, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.hasFlag(QualityFlag.VOLTAGE_ANOMALY)).isTrue();
    }

    @Test
    void amperageAnomaly() {
        TelemetryPayload reading = buildPayload(1140.0, 121.0, 25.0, 0.523);
        QualityResult result = validator.validate(reading, null);

        assertThat(result.hasFlag(QualityFlag.CURRENT_ANOMALY)).isTrue();
    }

    @Test
    void kwhRollback_flagsWhenDecreases() {
        TelemetryPayload previous = buildPayload(1140.0, 121.0, 9.4, 1.000);
        TelemetryPayload current = buildPayload(1140.0, 121.0, 9.4, 0.500);
        QualityResult result = validator.validate(current, previous);

        assertThat(result.hasFlag(QualityFlag.KWH_ROLLBACK)).isTrue();
    }

    @Test
    void wattageSpike_flagsLargeJump() {
        TelemetryPayload previous = buildPayload(100.0, 121.0, 0.8, 0.100);
        TelemetryPayload current = buildPayload(1200.0, 121.0, 9.9, 0.200);
        QualityResult result = validator.validate(current, previous);

        assertThat(result.hasFlag(QualityFlag.SPIKE_DETECTED)).isTrue();
    }

    @Test
    void normalIncrease_noSpike() {
        TelemetryPayload previous = buildPayload(1100.0, 121.0, 9.1, 0.100);
        TelemetryPayload current = buildPayload(1200.0, 121.0, 9.9, 0.200);
        QualityResult result = validator.validate(current, previous);

        assertThat(result.hasFlag(QualityFlag.SPIKE_DETECTED)).isFalse();
    }

    private TelemetryPayload buildPayload(Double wattage, Double voltage, Double amperage, Double totalKwh) {
        TelemetryPayload payload = new TelemetryPayload();
        payload.setWattage(wattage);
        payload.setVoltage(voltage);
        payload.setAmperage(amperage);
        payload.setTotalKwh(totalKwh);
        return payload;
    }
}
