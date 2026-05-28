package com.codedgiraffe.smartvolt.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cloud-test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "smartvolt.api.key=test-key",
    "smartvolt.device.id=kauf-01",
    "smartvolt.dashboard.password=test-pass"
})
class CloudApplicationTests {

    @Test
    void contextLoads() {
    }
}
