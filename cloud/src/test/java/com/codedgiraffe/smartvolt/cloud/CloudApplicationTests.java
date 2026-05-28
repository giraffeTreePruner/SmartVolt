package com.codedgiraffe.smartvolt.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/smartvolt",
    "spring.datasource.username=dev",
    "spring.datasource.password=dev",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
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
