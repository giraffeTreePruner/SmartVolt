package com.codedgiraffe.smartvolt.cloud.mqtt;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;
import com.codedgiraffe.smartvolt.cloud.service.TelemetryService;
import com.codedgiraffe.smartvolt.shared.util.KeyHashUtil;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryMessageHandlerTest {

    @Spy
    ObjectMapper mockObjMapper = new ObjectMapper();

    @Mock TelemetryService mockTeleService;
    @Mock DeviceService mockDeviceService;
    @Mock DeviceRepository mockDeviceRepo;
    @InjectMocks TelemetryMessageHandler mockTeleHandler;

    private static final String FAKE_RAW_KEY = "sk-kauf110-asdf";
    private static final String FAKE_HASH_KEY = KeyHashUtil.hash(FAKE_RAW_KEY);

    @Test
    void testHandler_ValidMessageCallsTelemetryForSensor() {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setDeviceKeyHash(FAKE_HASH_KEY);
        when(mockDeviceRepo.findByDeviceId("kauf-01"))
            .thenReturn(Optional.of(device));

        // Flat ESPHome payload (no nested ENERGY wrapper)
        String payload = "{\"key\":\"" + FAKE_RAW_KEY + "\","
            + "\"wattage\":1140,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.523}";
        Message<String> msg = MessageBuilder.withPayload(payload)
            .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/SENSOR")
            .build();

        mockTeleHandler.handleMessage(msg);

        verify(mockTeleService, times(1)).ingest(eq("kauf-01"), any());
        verify(mockDeviceService, never()).updateOnlineStatus(any(), anyBoolean());
    }

    @Test
    void testHandler_MissingKeyRejected() {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setDeviceKeyHash(FAKE_HASH_KEY);
        when(mockDeviceRepo.findByDeviceId("kauf-01"))
            .thenReturn(Optional.of(device));

        String payload = "{\"wattage\":1140,\"voltage\":121,\"amperage\":9.4}";
        Message<String> msg = MessageBuilder.withPayload(payload)
            .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/SENSOR")
            .build();

        mockTeleHandler.handleMessage(msg);

        verify(mockTeleService, never()).ingest(any(), any());
    }

    @Test
    void testHandler_InvalidKeyRejected() {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setDeviceKeyHash(FAKE_HASH_KEY);
        when(mockDeviceRepo.findByDeviceId("kauf-01"))
            .thenReturn(Optional.of(device));

        String payload = "{\"key\":\"invalid-key\",\"wattage\":1140,\"voltage\":121}";
        Message<String> msg = MessageBuilder.withPayload(payload)
            .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/SENSOR")
            .build();

        mockTeleHandler.handleMessage(msg);

        verify(mockTeleService, never()).ingest(any(), any());
    }

    @Test
    void testHandler_ValidOnlineUpdateForLWT() {
        Message<String> msg = MessageBuilder.withPayload("Online")
            .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/LWT")
            .build();

        mockTeleHandler.handleMessage(msg);

        verify(mockDeviceService, times(1)).updateOnlineStatus("kauf-01", true);
        verify(mockTeleService, never()).ingest(any(), any());
    }

    @Test
    void testHandler_ValidOfflineUpdateForLWT() {
        Message<String> msg = MessageBuilder.withPayload("Offline")
            .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/LWT")
            .build();

        mockTeleHandler.handleMessage(msg);

        verify(mockDeviceService, times(1)).updateOnlineStatus("kauf-01", false);
        verify(mockTeleService, never()).ingest(any(), any());
    }
}
