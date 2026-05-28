package com.codedgiraffe.smartvolt.edge.health;

import com.codedgiraffe.smartvolt.edge.buffer.BatchUploader;
import com.codedgiraffe.smartvolt.edge.buffer.BufferedReadingRepository;
import com.codedgiraffe.smartvolt.edge.mqtt.TelemetryHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class EdgeHealthController {

    private final BufferedReadingRepository bufferRepo;
    private final BatchUploader batchUploader;
    private final TelemetryHandler telemetryHandler;

    public EdgeHealthController(BufferedReadingRepository bufferRepo,
                                BatchUploader batchUploader,
                                TelemetryHandler telemetryHandler) {
        this.bufferRepo = bufferRepo;
        this.batchUploader = batchUploader;
        this.telemetryHandler = telemetryHandler;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "smartvolt-edge");
        result.put("timestamp", Instant.now().toString());
        result.put("bufferDepth", bufferRepo.countBySyncedFalse());
        result.put("lastMqttMessage", formatInstant(telemetryHandler.getLastMessageReceivedAt()));
        result.put("lastCloudUpload", formatInstant(batchUploader.getLastUploadAt()));
        result.put("lastUploadSuccessful", batchUploader.isLastUploadSuccessful());
        return result;
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "never";
    }
}
