package com.codedgiraffe.smartvolt.mqtt;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.codedgiraffe.smartvolt.dto.TelemetryPayload;
import com.codedgiraffe.smartvolt.model.Device;
import com.codedgiraffe.smartvolt.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.service.DeviceService;
import com.codedgiraffe.smartvolt.service.TelemetryService;
import com.codedgiraffe.smartvolt.util.KeyHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Component
public class TelemetryMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(TelemetryMessageHandler.class);

    private final TelemetryService telemetryService;
    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public TelemetryMessageHandler(TelemetryService telemetryService,
                                    DeviceService deviceService,
                                    DeviceRepository deviceRepository,
                                    ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload() instanceof String s ? s : "";

        if (topic == null || topic.isBlank()) {
            log.warn("Received message without a valid topic");
            return;
        }

        if (payload == null || payload.isBlank()) {
            log.warn("Received message with empty payload on topic: {}", topic);
            return;
        }

        // ESPHome publishes on the same topic structure via mqtt.publish_json
        if (topic.endsWith("/tele/SENSOR")) {
            handleTelemetry(topic, payload);
        } else if (topic.endsWith("/tele/LWT")) {
            handleLwt(topic, payload);
        } else {
            log.debug("Received message on unhandled topic: {}", topic);
        }
    }

    // Extract deviceID from topic
    // Topic format: smartvolt/devices/{deviceId}/tele/SENSOR
    // segments:     0         1         2        3    4
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
            // Safety Checks
            // Check 1, device must be registered
            Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
            if (deviceOpt.isEmpty()) {
                log.warn("Rejected telemetry from unregistered device: {}", deviceId);
                return;
            }

            // Check 2, key must be present from device always
            String incomingKey = data.getKey();
            if (incomingKey == null || incomingKey.isBlank()) {
                log.warn("Rejected telemetry from device {} due to missing key", deviceId);
                return;
            }

            // Check 3, constant-time hash comparison to prevent timing attacks
            String storedHash = deviceOpt.get().getDeviceKeyHash();
            String incomingHash = KeyHashUtil.hash(incomingKey);
            if (!MessageDigest.isEqual(
                    storedHash.getBytes(StandardCharsets.UTF_8),
                    incomingHash.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Rejected telemetry from device {} due to invalid key", deviceId);
                return;
            }

            telemetryService.ingest(deviceId, data);
        } catch (JacksonException e) {
            log.error("Malformed JSON from device {}: {}", deviceId, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Unexpected error while processing telemetry for device {}: {}", deviceId, e.getMessage());
            return;
        }
    }

    private void handleLwt(String topic, String payload) {
        String deviceId = extractDeviceId(topic);
        if (deviceId == null) return;

        // Payload is either "Online" or "Offline"
        boolean isOnline = "Online".equalsIgnoreCase(payload == null ? "" :payload.trim());
        deviceService.updateOnlineStatus(deviceId, isOnline);
        log.info("Device {} is now {}", deviceId, isOnline ? "ONLINE" : "OFFLINE");
    }
}
