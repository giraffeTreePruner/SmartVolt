package com.codedgiraffe.smartvolt.cloud.service;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryPayload;
import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock TelemetryRepository mockTeleRepo;
    @Mock DeviceRepository mockDeviceRepo;
    @InjectMocks TelemetryService mockTeleService;

    @Test
    void testIngestSaveKnownDevice() {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        when(mockDeviceRepo.findByDeviceId("kauf-01")).thenReturn(Optional.of(device));
        when(mockTeleRepo.save(any())).thenReturn(new TelemetryReading());
        TelemetryPayload payload = buildPayload(1140.0, 121.0, 9.4, 0.523);
        mockTeleService.ingest("kauf-01", payload);

        verify(mockTeleRepo, times(1)).save(any());
    }

    @Test
    void testIngestDropUnknownDevice() {
        when(mockDeviceRepo.findByDeviceId("unknown-device")).thenReturn(Optional.empty());
        TelemetryPayload payload = buildPayload(1140.0, 121.0, 9.4, 0.523);
        mockTeleService.ingest("unknown-device", payload);

        verify(mockTeleRepo, never()).save(any());
    }

    private TelemetryPayload buildPayload(Double wattage, Double voltage, Double amperage, Double totalKwh) {
        TelemetryPayload payload = new TelemetryPayload();
        payload.setWattage(wattage);
        payload.setVoltage(voltage);
        payload.setAmperage(amperage);
        payload.setTotalKwh(totalKwh);
        return payload;
    }
}
