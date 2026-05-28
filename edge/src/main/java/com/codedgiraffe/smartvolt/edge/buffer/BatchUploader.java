package com.codedgiraffe.smartvolt.edge.buffer;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BatchUploader {
    private static final Logger log = LoggerFactory.getLogger(BatchUploader.class);

    private final BufferedReadingRepository bufferRepo;
    private final RestClient cloudRestClient;

    private volatile Instant lastUploadAt;
    private volatile boolean lastUploadSuccessful;

    public BatchUploader(BufferedReadingRepository bufferRepo, RestClient cloudRestClient) {
        this.bufferRepo = bufferRepo;
        this.cloudRestClient = cloudRestClient;
    }

    public Instant getLastUploadAt() {
        return lastUploadAt;
    }

    public boolean isLastUploadSuccessful() {
        return lastUploadSuccessful;
    }

    @Scheduled(fixedRateString = "${smartvolt.batch.interval:30000}")
    public void uploadBatch() {
        List<BufferedReading> unsynced = bufferRepo.findTop100BySyncedFalseOrderByTimestampAsc();
        if (unsynced.isEmpty()) {
            return;
        }

        Map<String, List<BufferedReading>> byDevice = unsynced.stream()
                .collect(Collectors.groupingBy(BufferedReading::getDeviceId));

        for (Map.Entry<String, List<BufferedReading>> entry : byDevice.entrySet()) {
            String deviceId = entry.getKey();
            List<BufferedReading> readings = entry.getValue();

            TelemetryBatchRequest request = new TelemetryBatchRequest();
            request.setDeviceId(deviceId);
            request.setReadings(readings.stream().map(this::toReading).collect(Collectors.toList()));

            try {
                TelemetryBatchResponse response = cloudRestClient.post()
                        .uri("/api/telemetry/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(TelemetryBatchResponse.class);

                readings.forEach(r -> r.setSynced(true));
                bufferRepo.saveAll(readings);

                lastUploadAt = Instant.now();
                lastUploadSuccessful = true;

                log.info("Uploaded {} readings for device {} (accepted={}, rejected={})",
                        readings.size(), deviceId,
                        response != null ? response.getAccepted() : "?",
                        response != null ? response.getRejected() : "?");

            } catch (Exception e) {
                lastUploadSuccessful = false;
                log.warn("Failed to upload batch for device {}: {} - will retry next cycle",
                        deviceId, e.getMessage());
            }
        }
    }

    private TelemetryBatchRequest.Reading toReading(BufferedReading br) {
        TelemetryBatchRequest.Reading r = new TelemetryBatchRequest.Reading();
        r.setTimestamp(br.getTimestamp());
        r.setWattage(br.getWattage());
        r.setVoltage(br.getVoltage());
        r.setAmperage(br.getAmperage());
        r.setTotalKwh(br.getTotalKwh());
        if (br.getQualityFlags() != null && !br.getQualityFlags().isBlank()) {
            r.setQualityFlags(Arrays.asList(br.getQualityFlags().split(",")));
        }
        return r;
    }
}
