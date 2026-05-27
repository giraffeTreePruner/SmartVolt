package com.codedgiraffe.smartvolt.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class CommandPublisher {
    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);
    private final MessageChannel mqttOutputChannel;

    @Value("${smartvolt.device.id}")
    private String defaultDeviceId;

    public CommandPublisher(@Qualifier("mqttOutputChannel") MessageChannel mqttOutputChannel) {
        this.mqttOutputChannel = mqttOutputChannel;
    }

    public void setPower(boolean on) {
        setPower(defaultDeviceId, on);
    }

    public void setPower(String deviceId, boolean on) {
        String topic = "smartvolt/devices/" + deviceId + "/cmnd/Power";
        String payload = on ? "ON" : "OFF";

        Message<String> message = MessageBuilder
            .withPayload(payload)
            .setHeader(MqttHeaders.TOPIC, topic)
            .build();

        mqttOutputChannel.send(message);
        log.info("Sent power command to device {}: {}", deviceId, payload);
    }
}
