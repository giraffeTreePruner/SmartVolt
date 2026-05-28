package com.codedgiraffe.smartvolt.cloud.dashboard;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardApiController.class)
@TestPropertySource(properties = {
    "smartvolt.api.key=test-api-key",
    "smartvolt.dashboard.password=test-pass"
})
class DashboardApiControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean DeviceService deviceService;
    @MockitoBean TelemetryRepository telemetryRepo;

    @Test
    @WithMockUser
    void liveReadings_returnsFragment() throws Exception {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setOnline(true);
        when(deviceService.findAll()).thenReturn(List.of(device));

        TelemetryReading reading = new TelemetryReading();
        reading.setWattage(85.0);
        reading.setVoltage(120.5);
        reading.setAmperage(0.7);
        reading.setTotalKwh(3.456);
        when(telemetryRepo.findTopByDeviceIdOrderByTimestampDesc("kauf-01"))
                .thenReturn(Optional.of(reading));

        mockMvc.perform(get("/dashboard/api/live"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void historyData_returnsJson() throws Exception {
        TelemetryReading r = new TelemetryReading();
        r.setTimestamp(Instant.parse("2026-05-28T10:00:00Z"));
        r.setWattage(150.0);
        r.setVoltage(121.0);
        r.setAmperage(1.2);

        when(telemetryRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                eq("kauf-01"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(r));

        mockMvc.perform(get("/dashboard/api/history/data")
                        .param("deviceId", "kauf-01")
                        .param("from", "2026-05-28T00:00:00Z")
                        .param("to", "2026-05-28T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].wattage").value(150.0))
                .andExpect(jsonPath("$[0].voltage").value(121.0))
                .andExpect(jsonPath("$[0].timestamp").value("2026-05-28T10:00:00Z"));
    }

}
