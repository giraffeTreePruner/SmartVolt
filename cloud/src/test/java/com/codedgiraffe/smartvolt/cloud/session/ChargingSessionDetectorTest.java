package com.codedgiraffe.smartvolt.cloud.session;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargingSessionDetectorTest {

    @Mock
    private ChargingSessionRepository sessionRepo;

    private ChargingSessionDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ChargingSessionDetector(sessionRepo);
    }

    @Test
    void threeHighReadings_startsSession() {
        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(150.0, 160.0, 170.0));

        detector.processBatch(batch);

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepo).save(captor.capture());
        ChargingSession saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo("kauf-01");
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void twoHighReadings_doesNotStartSession() {
        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(150.0, 160.0));

        detector.processBatch(batch);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void highThenLow_resetsCounter() {
        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(150.0, 160.0, 30.0, 150.0, 160.0));

        detector.processBatch(batch);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void sixLowReadings_endsActiveSession() {
        ChargingSession active = new ChargingSession();
        active.setId("session-1");
        active.setDeviceId("kauf-01");
        active.setStartKwh(10.0);
        active.setStatus(SessionStatus.ACTIVE);

        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));
        when(sessionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(30.0, 20.0, 10.0, 5.0, 3.0, 0.0),
                12.5);

        detector.processBatch(batch);

        ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
        verify(sessionRepo).save(captor.capture());
        ChargingSession ended = captor.getValue();
        assertThat(ended.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEnergyUsedKwh()).isEqualTo(2.5);
    }

    @Test
    void fiveLowReadings_doesNotEndSession() {
        ChargingSession active = new ChargingSession();
        active.setId("session-1");
        active.setDeviceId("kauf-01");
        active.setStatus(SessionStatus.ACTIVE);

        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(30.0, 20.0, 10.0, 5.0, 3.0));

        detector.processBatch(batch);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void lowReadingsDuringIdle_noEffect() {
        when(sessionRepo.findByDeviceIdAndStatus("kauf-01", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TelemetryBatchRequest batch = createBatch("kauf-01",
                List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));

        detector.processBatch(batch);

        verify(sessionRepo, never()).save(any());
    }

    private TelemetryBatchRequest createBatch(String deviceId, List<Double> wattages) {
        return createBatch(deviceId, wattages, null);
    }

    private TelemetryBatchRequest createBatch(String deviceId, List<Double> wattages,
                                              Double totalKwh) {
        TelemetryBatchRequest batch = new TelemetryBatchRequest();
        batch.setDeviceId(deviceId);
        batch.setReadings(IntStream.range(0, wattages.size())
                .mapToObj(i -> {
                    TelemetryBatchRequest.Reading r = new TelemetryBatchRequest.Reading();
                    r.setTimestamp(Instant.now().minusSeconds((wattages.size() - i) * 10L));
                    r.setWattage(wattages.get(i));
                    r.setVoltage(121.0);
                    r.setAmperage(wattages.get(i) / 121.0);
                    r.setTotalKwh(totalKwh);
                    return r;
                })
                .collect(Collectors.toList()));
        return batch;
    }
}
