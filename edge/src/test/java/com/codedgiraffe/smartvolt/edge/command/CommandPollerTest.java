package com.codedgiraffe.smartvolt.edge.command;

import com.codedgiraffe.smartvolt.shared.dto.CommandDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandPollerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient cloudRestClient;

    @Mock
    private LocalMqttPublisher mqttPublisher;

    private CommandPoller poller;

    @BeforeEach
    void setUp() {
        poller = new CommandPoller(cloudRestClient, mqttPublisher, "kauf-01");
    }

    @SuppressWarnings("unchecked")
    @Test
    void noCommands_doesNothing() {
        when(cloudRestClient.get()
                .uri(anyString(), any(Object[].class))
                .retrieve()
                .body(any(ParameterizedTypeReference.class)))
                .thenReturn(Collections.emptyList());

        poller.pollCommands();

        verifyNoInteractions(mqttPublisher);
    }

    @SuppressWarnings("unchecked")
    @Test
    void powerOnCommand_publishesOn() {
        CommandDto cmd = createCommand("cmd-1", "kauf-01", "POWER_ON");
        when(cloudRestClient.get()
                .uri(anyString(), any(Object[].class))
                .retrieve()
                .body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(cmd));

        when(cloudRestClient.post()
                .uri(anyString(), any(Object[].class))
                .contentType(any())
                .body(any())
                .retrieve()
                .toBodilessEntity())
                .thenReturn(null);

        poller.pollCommands();

        verify(mqttPublisher).publishPower("kauf-01", true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void powerOffCommand_publishesOff() {
        CommandDto cmd = createCommand("cmd-2", "kauf-01", "POWER_OFF");
        when(cloudRestClient.get()
                .uri(anyString(), any(Object[].class))
                .retrieve()
                .body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(cmd));

        when(cloudRestClient.post()
                .uri(anyString(), any(Object[].class))
                .contentType(any())
                .body(any())
                .retrieve()
                .toBodilessEntity())
                .thenReturn(null);

        poller.pollCommands();

        verify(mqttPublisher).publishPower("kauf-01", false);
    }

    @Test
    void connectionFailure_doesNotThrow() {
        when(cloudRestClient.get()).thenThrow(new RuntimeException("Connection refused"));

        poller.pollCommands();

        verifyNoInteractions(mqttPublisher);
    }

    private CommandDto createCommand(String id, String deviceId, String type) {
        CommandDto cmd = new CommandDto();
        cmd.setId(id);
        cmd.setDeviceId(deviceId);
        cmd.setCommandType(type);
        cmd.setExpiresAt(Instant.now().plusSeconds(300));
        return cmd;
    }
}
