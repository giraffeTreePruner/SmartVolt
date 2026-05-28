package com.codedgiraffe.smartvolt.cloud.dashboard;

import com.codedgiraffe.smartvolt.cloud.command.CommandService;
import com.codedgiraffe.smartvolt.cloud.command.CommandType;
import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSession;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSessionRepository;
import com.codedgiraffe.smartvolt.cloud.session.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@TestPropertySource(properties = {
    "smartvolt.api.key=test-api-key",
    "smartvolt.dashboard.password=test-pass"
})
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean DeviceService deviceService;
    @MockitoBean TelemetryRepository telemetryRepo;
    @MockitoBean CommandService commandService;
    @MockitoBean ChargingSessionRepository sessionRepo;

    @Test
    void loginPage_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @WithMockUser
    void dashboard_authenticated_returnsView() throws Exception {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setOnline(true);
        when(deviceService.findAll()).thenReturn(List.of(device));

        TelemetryReading reading = new TelemetryReading();
        reading.setWattage(120.5);
        reading.setVoltage(121.0);
        reading.setAmperage(1.0);
        reading.setTotalKwh(5.123);
        when(telemetryRepo.findTopByDeviceIdOrderByTimestampDesc("kauf-01"))
                .thenReturn(Optional.of(reading));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("devices", "latestReadings"));
    }

    @Test
    @WithMockUser
    void history_authenticated_returnsView() throws Exception {
        when(deviceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attributeExists("devices"));
    }

    @Test
    @WithMockUser
    void sessions_authenticated_returnsView() throws Exception {
        ChargingSession session = new ChargingSession();
        session.setDeviceId("kauf-01");
        session.setStartedAt(Instant.now().minusSeconds(3600));
        session.setEndedAt(Instant.now());
        session.setEnergyUsedKwh(2.5);
        session.setStatus(SessionStatus.COMPLETED);
        when(sessionRepo.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(session));

        mockMvc.perform(get("/dashboard/sessions"))
                .andExpect(status().isOk())
                .andExpect(view().name("sessions"))
                .andExpect(model().attributeExists("sessions"));
    }

    @Test
    @WithMockUser
    void togglePower_createsCommandAndRedirects() throws Exception {
        Device device = new Device();
        device.setDeviceId("kauf-01");
        when(deviceService.findByDeviceId("kauf-01")).thenReturn(Optional.of(device));

        mockMvc.perform(post("/dashboard/devices/kauf-01/power")
                        .param("on", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        verify(commandService).createCommand("kauf-01", CommandType.POWER_ON);
    }

    @Test
    @WithMockUser
    void togglePower_unknownDevice_returns404() throws Exception {
        when(deviceService.findByDeviceId("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(post("/dashboard/devices/unknown/power")
                        .param("on", "false")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
