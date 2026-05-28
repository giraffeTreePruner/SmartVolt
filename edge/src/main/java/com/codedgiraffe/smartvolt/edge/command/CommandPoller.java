package com.codedgiraffe.smartvolt.edge.command;

import com.codedgiraffe.smartvolt.shared.dto.CommandAckRequest;
import com.codedgiraffe.smartvolt.shared.dto.CommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.List;

@Component
public class CommandPoller {
    private static final Logger log = LoggerFactory.getLogger(CommandPoller.class);

    private final RestClient cloudRestClient;
    private final LocalMqttPublisher mqttPublisher;
    private final String deviceId;

    public CommandPoller(RestClient cloudRestClient,
                         LocalMqttPublisher mqttPublisher,
                         @Value("${smartvolt.device.id:kauf-01}") String deviceId) {
        this.cloudRestClient = cloudRestClient;
        this.mqttPublisher = mqttPublisher;
        this.deviceId = deviceId;
    }

    @Scheduled(fixedRateString = "${smartvolt.command.poll-interval:5000}")
    public void pollCommands() {
        try {
            List<CommandDto> commands = cloudRestClient.get()
                    .uri("/api/commands/pending?deviceId={deviceId}", deviceId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (commands == null || commands.isEmpty()) {
                return;
            }

            for (CommandDto cmd : commands) {
                executeCommand(cmd);
            }
        } catch (Exception e) {
            log.debug("Failed to poll commands: {}", e.getMessage());
        }
    }

    private void executeCommand(CommandDto cmd) {
        try {
            switch (cmd.getCommandType()) {
                case "POWER_ON" -> mqttPublisher.publishPower(cmd.getDeviceId(), true);
                case "POWER_OFF" -> mqttPublisher.publishPower(cmd.getDeviceId(), false);
                default -> {
                    log.warn("Unknown command type: {}", cmd.getCommandType());
                    return;
                }
            }

            acknowledgeCommand(cmd.getId(), "ACKNOWLEDGED");
            log.info("Executed command {} ({}) for device {}",
                    cmd.getId(), cmd.getCommandType(), cmd.getDeviceId());

        } catch (Exception e) {
            log.error("Failed to execute command {} for device {}: {}",
                    cmd.getId(), cmd.getDeviceId(), e.getMessage());
            acknowledgeCommand(cmd.getId(), "FAILED");
        }
    }

    private void acknowledgeCommand(String commandId, String status) {
        try {
            cloudRestClient.post()
                    .uri("/api/commands/{id}/ack", commandId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CommandAckRequest(commandId, status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to acknowledge command {}: {}", commandId, e.getMessage());
        }
    }
}
