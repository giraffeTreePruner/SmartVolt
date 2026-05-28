package com.codedgiraffe.smartvolt.cloud.ingest;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
class S3ArchivalServiceTest {

    @Test
    void archiveBatch_disabled_doesNotThrow() {
        S3ArchivalService service = new S3ArchivalService(new ObjectMapper());

        TelemetryBatchRequest batch = new TelemetryBatchRequest();
        batch.setDeviceId("kauf-01");
        TelemetryBatchRequest.Reading r = new TelemetryBatchRequest.Reading();
        r.setTimestamp(Instant.now());
        r.setWattage(1140.0);
        r.setVoltage(121.0);
        r.setAmperage(9.4);
        r.setTotalKwh(0.5);
        batch.setReadings(List.of(r));

        assertThatNoException().isThrownBy(() -> service.archiveBatch(batch));
    }
}
