package com.codedgiraffe.smartvolt.edge.mqtt;

import com.codedgiraffe.smartvolt.edge.buffer.BufferedReading;
import com.codedgiraffe.smartvolt.edge.buffer.BufferedReadingRepository;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryPayload;
import com.codedgiraffe.smartvolt.shared.util.KeyHashUtil;
import com.codedgiraffe.smartvolt.shared.validation.DataQualityValidator;
import com.codedgiraffe.smartvolt.shared.validation.QualityResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class TelemetryHandler {
    private static final Logger log = LoggerFactory.getLogger(TelemetryHandler.class);

    private final BufferedReadingRepository bufferRepo;
    private final ObjectMapper objectMapper;
    private final DataQualityValidator validator;
    private final Map<String, String> deviceKeyHashes;
    private final Map<String, TelemetryPayload> previousReadings = new ConcurrentHashMap<>();

    private volatile Instant lastMessageReceivedAt;

    public TelemetryHandler(BufferedReadingRepository bufferRepo,
                            ObjectMapper objectMapper,
                            @Value("#{${smartvolt.device-keys:{}}}") Map<String, String> deviceKeys) {
        this.bufferRepo = bufferRepo;
        this.objectMapper = objectMapper;
        this.validator = new DataQualityValidator();
        this.deviceKeyHashes = deviceKeys.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> KeyHashUtil.hash(e.getValue())));
    }

    public Instant getLastMessageReceivedAt() {
        return lastMessageReceivedAt;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload() instanceof String s ? s : "";

        if (topic == null || topic.isBlank()) {
            log.warn("Received message without a valid topic");
            return;
        }

        lastMessageReceivedAt = Instant.now();

        if (topic.endsWith("/tele/SENSOR")) {
            handleTelemetry(topic, payload);
        } else if (topic.endsWith("/tele/LWT")) {
            handleLwt(topic, payload);
        } else {
            log.debug("Ignoring message on unhandled topic: {}", topic);
        }
    }

    private String extractDeviceId(String topic) {
        String[] segments = topic.split("/");
        if (segments.length < 4) {
            log.warn("Malformed topic - cannot extract deviceId: {}", topic);
            return null;
        }
        return segments[2];
    }

    private void handleTelemetry(String topic, String payload) {
        String deviceId = extractDeviceId(topic);
        if (deviceId == null) return;

        try {
            TelemetryPayload data = objectMapper.readValue(payload, TelemetryPayload.class);

            if (!validateDeviceKey(deviceId, data.getKey())) {
                return;
            }

            TelemetryPayload previous = previousReadings.get(deviceId);
            QualityResult quality = validator.validate(data, previous);
            previousReadings.put(deviceId, data);

            if (!quality.isValid()) {
                log.warn("Rejected invalid reading from device {}: flags={}", deviceId, quality.getFlags());
                return;
            }

            BufferedReading reading = new BufferedReading();
            reading.setDeviceId(deviceId);
            reading.setTimestamp(Instant.now());
            reading.setWattage(data.getWattage());
            reading.setVoltage(data.getVoltage());
            reading.setAmperage(data.getAmperage());
            reading.setTotalKwh(data.getTotalKwh());
            reading.setSynced(false);

            if (!quality.getFlags().isEmpty()) {
                String flags = quality.getFlags().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(","));
                reading.setQualityFlags(flags);
            }

            bufferRepo.save(reading);
            log.debug("Buffered reading from device {} (flags: {})", deviceId,
                    quality.getFlags().isEmpty() ? "none" : quality.getFlags());

        } catch (JacksonException e) {
            log.error("Malformed JSON from device {}: {}", deviceId, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing telemetry for device {}: {}", deviceId, e.getMessage());
        }
    }

    private boolean validateDeviceKey(String deviceId, String incomingKey) {
        if (incomingKey == null || incomingKey.isBlank()) {
            log.warn("Rejected telemetry from device {} - missing key", deviceId);
            return false;
        }

        String expectedHash = deviceKeyHashes.get(deviceId);
        if (expectedHash == null) {
            log.warn("Rejected telemetry from unknown device {}", deviceId);
            return false;
        }

        String incomingHash = KeyHashUtil.hash(incomingKey);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                incomingHash.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected telemetry from device {} - invalid key", deviceId);
            return false;
        }

        return true;
    }

    private void handleLwt(String topic, String payload) {
        String deviceId = extractDeviceId(topic);
        if (deviceId == null) return;

        boolean isOnline = "Online".equalsIgnoreCase(payload == null ? "" : payload.trim());
        log.info("Device {} is now {}", deviceId, isOnline ? "ONLINE" : "OFFLINE");
    }
}
