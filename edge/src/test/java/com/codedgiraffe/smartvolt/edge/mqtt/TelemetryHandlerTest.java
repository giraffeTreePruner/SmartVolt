package com.codedgiraffe.smartvolt.edge.mqtt;

import com.codedgiraffe.smartvolt.edge.buffer.BufferedReading;
import com.codedgiraffe.smartvolt.edge.buffer.BufferedReadingRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryHandlerTest {

    @Mock
    private BufferedReadingRepository bufferRepo;

    private TelemetryHandler handler;

    private static final String DEVICE_KEY = "sk-kauf110-asdf";

    @BeforeEach
    void setUp() {
        Map<String, String> deviceKeys = Map.of("kauf-01", DEVICE_KEY);
        handler = new TelemetryHandler(bufferRepo, new ObjectMapper(), deviceKeys);
    }

    @Test
    void validTelemetry_buffersReading() {
        String payload = "{\"key\":\"" + DEVICE_KEY + "\","
                + "\"wattage\":1140,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.523}";
        Message<String> msg = buildSensorMessage("kauf-01", payload);

        when(bufferRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        handler.handleMessage(msg);

        ArgumentCaptor<BufferedReading> captor = ArgumentCaptor.forClass(BufferedReading.class);
        verify(bufferRepo).save(captor.capture());

        BufferedReading saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo("kauf-01");
        assertThat(saved.getWattage()).isEqualTo(1140.0);
        assertThat(saved.getVoltage()).isEqualTo(121.0);
        assertThat(saved.getAmperage()).isEqualTo(9.4);
        assertThat(saved.getTotalKwh()).isEqualTo(0.523);
        assertThat(saved.isSynced()).isFalse();
    }

    @Test
    void missingKey_rejected() {
        String payload = "{\"wattage\":1140,\"voltage\":121,\"amperage\":9.4}";
        Message<String> msg = buildSensorMessage("kauf-01", payload);

        handler.handleMessage(msg);

        verify(bufferRepo, never()).save(any());
    }

    @Test
    void invalidKey_rejected() {
        String payload = "{\"key\":\"wrong-key\","
                + "\"wattage\":1140,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.5}";
        Message<String> msg = buildSensorMessage("kauf-01", payload);

        handler.handleMessage(msg);

        verify(bufferRepo, never()).save(any());
    }

    @Test
    void unknownDevice_rejected() {
        String payload = "{\"key\":\"" + DEVICE_KEY + "\","
                + "\"wattage\":1140,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.5}";
        Message<String> msg = buildSensorMessage("unknown-device", payload);

        handler.handleMessage(msg);

        verify(bufferRepo, never()).save(any());
    }

    @Test
    void invalidReading_missingFields_rejected() {
        String payload = "{\"key\":\"" + DEVICE_KEY + "\",\"wattage\":100}";
        Message<String> msg = buildSensorMessage("kauf-01", payload);

        handler.handleMessage(msg);

        verify(bufferRepo, never()).save(any());
    }

    @Test
    void qualityFlags_storedOnReading() {
        String payload = "{\"key\":\"" + DEVICE_KEY + "\","
                + "\"wattage\":100,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.5}";
        Message<String> msg1 = buildSensorMessage("kauf-01", payload);
        when(bufferRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        handler.handleMessage(msg1);

        String spikePayload = "{\"key\":\"" + DEVICE_KEY + "\","
                + "\"wattage\":1100,\"voltage\":121,\"amperage\":9.4,\"totalKwh\":0.6}";
        Message<String> msg2 = buildSensorMessage("kauf-01", spikePayload);
        handler.handleMessage(msg2);

        ArgumentCaptor<BufferedReading> captor = ArgumentCaptor.forClass(BufferedReading.class);
        verify(bufferRepo, times(2)).save(captor.capture());

        BufferedReading spikeReading = captor.getAllValues().get(1);
        assertThat(spikeReading.getQualityFlags()).contains("SPIKE_DETECTED");
    }

    @Test
    void malformedJson_doesNotThrow() {
        Message<String> msg = buildSensorMessage("kauf-01", "not json at all");

        handler.handleMessage(msg);

        verify(bufferRepo, never()).save(any());
    }

    @Test
    void lwtMessage_tracksLastMessageTime() {
        Message<String> msg = MessageBuilder.withPayload("Online")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "smartvolt/devices/kauf-01/tele/LWT")
                .build();

        handler.handleMessage(msg);

        assertThat(handler.getLastMessageReceivedAt()).isNotNull();
        verify(bufferRepo, never()).save(any());
    }

    private Message<String> buildSensorMessage(String deviceId, String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartvolt/devices/" + deviceId + "/tele/SENSOR")
                .build();
    }
}
