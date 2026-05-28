package com.codedgiraffe.smartvolt.cloud.ingest;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestServiceTest {

    @Mock
    private TelemetryRepository telemetryRepo;

    @Mock
    private DeviceRepository deviceRepo;

    @InjectMocks
    private TelemetryIngestService ingestService;

    @Test
    void ingestBatch_knownDevice_acceptsReadings() {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        when(deviceRepo.findByDeviceId("kauf-01")).thenReturn(Optional.of(device));
        when(telemetryRepo.save(any())).thenReturn(new TelemetryReading());

        TelemetryBatchRequest batch = createBatch("kauf-01", 3);

        TelemetryBatchResponse response = ingestService.ingestBatch(batch);

        assertThat(response.getAccepted()).isEqualTo(3);
        assertThat(response.getRejected()).isEqualTo(0);
        verify(telemetryRepo, times(3)).save(any());
    }

    @Test
    void ingestBatch_unknownDevice_rejectsAll() {
        when(deviceRepo.findByDeviceId("unknown")).thenReturn(Optional.empty());

        TelemetryBatchRequest batch = createBatch("unknown", 2);

        TelemetryBatchResponse response = ingestService.ingestBatch(batch);

        assertThat(response.getAccepted()).isEqualTo(0);
        assertThat(response.getRejected()).isEqualTo(2);
        verify(telemetryRepo, never()).save(any());
    }

    private TelemetryBatchRequest createBatch(String deviceId, int count) {
        TelemetryBatchRequest batch = new TelemetryBatchRequest();
        batch.setDeviceId(deviceId);
        batch.setReadings(java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    TelemetryBatchRequest.Reading r = new TelemetryBatchRequest.Reading();
                    r.setTimestamp(Instant.now().minusSeconds(i * 10L));
                    r.setWattage(1140.0);
                    r.setVoltage(121.0);
                    r.setAmperage(9.4);
                    r.setTotalKwh(0.5 + i * 0.01);
                    return r;
                })
                .collect(java.util.stream.Collectors.toList()));
        return batch;
    }
}
