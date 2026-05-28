package com.codedgiraffe.smartvolt.cloud.ingest;

import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSessionDetector;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestService.class);

    private final TelemetryRepository telemetryRepo;
    private final DeviceRepository deviceRepo;
    private final S3ArchivalService s3Archiver;
    private final ChargingSessionDetector sessionDetector;

    public TelemetryIngestService(TelemetryRepository telemetryRepo,
                                  DeviceRepository deviceRepo,
                                  S3ArchivalService s3Archiver,
                                  ChargingSessionDetector sessionDetector) {
        this.telemetryRepo = telemetryRepo;
        this.deviceRepo = deviceRepo;
        this.s3Archiver = s3Archiver;
        this.sessionDetector = sessionDetector;
    }

    public TelemetryBatchResponse ingestBatch(TelemetryBatchRequest batch) {
        String deviceId = batch.getDeviceId();

        if (deviceRepo.findByDeviceId(deviceId).isEmpty()) {
            log.warn("Rejected batch from unregistered device: {}", deviceId);
            return new TelemetryBatchResponse(0, batch.getReadings().size());
        }

        int accepted = 0;
        int rejected = 0;

        for (TelemetryBatchRequest.Reading r : batch.getReadings()) {
            try {
                TelemetryReading reading = new TelemetryReading();
                reading.setDeviceId(deviceId);
                reading.setTimestamp(r.getTimestamp());
                reading.setWattage(r.getWattage());
                reading.setVoltage(r.getVoltage());
                reading.setAmperage(r.getAmperage());
                reading.setTotalKwh(r.getTotalKwh());
                telemetryRepo.save(reading);
                accepted++;
            } catch (Exception e) {
                log.warn("Failed to persist reading for device {}: {}", deviceId, e.getMessage());
                rejected++;
            }
        }

        log.info("Ingested batch for device {}: {} accepted, {} rejected",
                deviceId, accepted, rejected);

        s3Archiver.archiveBatch(batch);
        sessionDetector.processBatch(batch);

        return new TelemetryBatchResponse(accepted, rejected);
    }
}
