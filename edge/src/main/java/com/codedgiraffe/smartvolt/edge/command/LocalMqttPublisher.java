package com.codedgiraffe.smartvolt.edge.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

@Component
public class LocalMqttPublisher {
    private static final Logger log = LoggerFactory.getLogger(LocalMqttPublisher.class);

    private final MessageChannel mqttOutputChannel;

    public LocalMqttPublisher(@Qualifier("mqttOutputChannel") MessageChannel mqttOutputChannel) {
        this.mqttOutputChannel = mqttOutputChannel;
    }

    public void publishPower(String deviceId, boolean on) {
        String topic = "smartvolt/devices/" + deviceId + "/cmnd/Power";
        String payload = on ? "ON" : "OFF";

        mqttOutputChannel.send(
                MessageBuilder.withPayload(payload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .build());

        log.info("Published power command to device {}: {}", deviceId, payload);
    }
}
