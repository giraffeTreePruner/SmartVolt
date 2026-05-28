package com.codedgiraffe.smartvolt.cloud.service;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryPayload;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TelemetryService {

    private final TelemetryRepository telemetryRepo;
    private final DeviceRepository deviceRepo;

    public TelemetryService(TelemetryRepository tr, DeviceRepository dr) {
        this.telemetryRepo = tr;
        this.deviceRepo = dr;
    }

    public void ingest(String deviceId, TelemetryPayload data) {
        deviceRepo.findByDeviceId(deviceId).ifPresent(device -> {
            TelemetryReading reading = new TelemetryReading();
            reading.setDeviceId(deviceId);
            reading.setWattage(data.getWattage());
            reading.setVoltage(data.getVoltage());
            reading.setAmperage(data.getAmperage());
            reading.setTotalKwh(data.getTotalKwh());
            telemetryRepo.save(reading);
        });
    }

    public Page<TelemetryReading> query(String deviceId, Instant start,
                                        Instant end, Pageable pageable) {
        return telemetryRepo.findByDeviceIdAndTimestampBetween(deviceId, start, end, pageable);
    }
}
