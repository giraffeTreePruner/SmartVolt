package com.codedgiraffe.smartvolt.cloud.session;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChargingSessionDetector {
    private static final Logger log = LoggerFactory.getLogger(ChargingSessionDetector.class);

    static final double CHARGING_THRESHOLD = 100.0;
    static final double IDLE_THRESHOLD = 50.0;
    static final int READINGS_TO_START = 3;
    static final int READINGS_TO_STOP = 6;

    private final ChargingSessionRepository sessionRepo;
    private final Map<String, DeviceState> deviceStates = new ConcurrentHashMap<>();

    public ChargingSessionDetector(ChargingSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public void processBatch(TelemetryBatchRequest batch) {
        String deviceId = batch.getDeviceId();
        DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());

        for (TelemetryBatchRequest.Reading reading : batch.getReadings()) {
            processReading(deviceId, reading, state);
        }
    }

    private void processReading(String deviceId, TelemetryBatchRequest.Reading reading,
                                DeviceState state) {
        if (reading.getWattage() == null) return;

        double wattage = reading.getWattage();
        Instant timestamp = reading.getTimestamp() != null ? reading.getTimestamp() : Instant.now();

        boolean isActive = sessionRepo.findByDeviceIdAndStatus(deviceId, SessionStatus.ACTIVE)
                .isPresent();

        if (!isActive) {
            if (wattage > CHARGING_THRESHOLD) {
                state.consecutiveHigh++;
                state.consecutiveLow = 0;
                if (state.consecutiveHigh >= READINGS_TO_START) {
                    startSession(deviceId, timestamp, reading.getTotalKwh());
                    state.consecutiveHigh = 0;
                }
            } else {
                state.consecutiveHigh = 0;
            }
        } else {
            if (wattage < IDLE_THRESHOLD) {
                state.consecutiveLow++;
                state.consecutiveHigh = 0;
                if (state.consecutiveLow >= READINGS_TO_STOP) {
                    endSession(deviceId, timestamp, reading.getTotalKwh());
                    state.consecutiveLow = 0;
                }
            } else {
                state.consecutiveLow = 0;
            }
        }
    }

    private void startSession(String deviceId, Instant timestamp, Double totalKwh) {
        ChargingSession session = new ChargingSession();
        session.setDeviceId(deviceId);
        session.setStartedAt(timestamp);
        session.setStartKwh(totalKwh);
        session.setStatus(SessionStatus.ACTIVE);
        sessionRepo.save(session);
        log.info("Charging session started for device {} at {}", deviceId, timestamp);
    }

    private void endSession(String deviceId, Instant timestamp, Double totalKwh) {
        sessionRepo.findByDeviceIdAndStatus(deviceId, SessionStatus.ACTIVE)
                .ifPresent(session -> {
                    session.setEndedAt(timestamp);
                    session.setEndKwh(totalKwh);
                    if (session.getStartKwh() != null && totalKwh != null) {
                        session.setEnergyUsedKwh(totalKwh - session.getStartKwh());
                    }
                    session.setStatus(SessionStatus.COMPLETED);
                    sessionRepo.save(session);
                    log.info("Charging session completed for device {} (energy: {} kWh)",
                            deviceId, session.getEnergyUsedKwh());
                });
    }

    static class DeviceState {
        int consecutiveHigh;
        int consecutiveLow;
    }
}
