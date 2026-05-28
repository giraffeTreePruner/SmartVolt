package com.codedgiraffe.smartvolt.edge.buffer;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchUploaderTest {

    @Mock
    private BufferedReadingRepository bufferRepo;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient cloudRestClient;

    private BatchUploader uploader;

    @BeforeEach
    void setUp() {
        uploader = new BatchUploader(bufferRepo, cloudRestClient);
    }

    @Test
    void nothingToUpload_skips() {
        when(bufferRepo.findTop100BySyncedFalseOrderByTimestampAsc())
                .thenReturn(Collections.emptyList());

        uploader.uploadBatch();

        verifyNoInteractions(cloudRestClient);
    }

    @Test
    void successfulUpload_marksSynced() {
        BufferedReading reading = createReading("kauf-01", 1140.0, 121.0, 9.4);
        when(bufferRepo.findTop100BySyncedFalseOrderByTimestampAsc())
                .thenReturn(List.of(reading));

        when(cloudRestClient.post()
                .uri(anyString())
                .contentType(any())
                .body(any())
                .retrieve()
                .body(eq(TelemetryBatchResponse.class)))
                .thenReturn(new TelemetryBatchResponse(1, 0));

        uploader.uploadBatch();

        assertThat(reading.isSynced()).isTrue();
        verify(bufferRepo).saveAll(List.of(reading));
        assertThat(uploader.getLastUploadAt()).isNotNull();
        assertThat(uploader.isLastUploadSuccessful()).isTrue();
    }

    @Test
    void failedUpload_doesNotMarkSynced() {
        BufferedReading reading = createReading("kauf-01", 1140.0, 121.0, 9.4);
        when(bufferRepo.findTop100BySyncedFalseOrderByTimestampAsc())
                .thenReturn(List.of(reading));

        when(cloudRestClient.post()).thenThrow(new RuntimeException("Connection refused"));

        uploader.uploadBatch();

        assertThat(reading.isSynced()).isFalse();
        verify(bufferRepo, never()).saveAll(any());
        assertThat(uploader.isLastUploadSuccessful()).isFalse();
    }

    private BufferedReading createReading(String deviceId, double wattage, double voltage, double amperage) {
        BufferedReading r = new BufferedReading();
        r.setDeviceId(deviceId);
        r.setTimestamp(Instant.now());
        r.setWattage(wattage);
        r.setVoltage(voltage);
        r.setAmperage(amperage);
        r.setTotalKwh(0.5);
        r.setSynced(false);
        return r;
    }
}
