package com.codedgiraffe.smartvolt.cloud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cloud-test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration",
    "mqtt.broker.url=tcp://localhost:1883",
    "mqtt.username=test",
    "mqtt.password=test",
    "mqtt.tls.ca-cert-path=",
    "smartvolt.api.key=test-key",
    "smartvolt.device.id=kauf-01"
})
class CloudApplicationTests {

    @Test
    void contextLoads() {
    }
}
