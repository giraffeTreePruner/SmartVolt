package com.codedgiraffe.smartvolt.cloud.controller;

import com.codedgiraffe.smartvolt.cloud.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TelemetryController.class)
@TestPropertySource(properties = "smartvolt.api.key=test-api-key")
class TelemetryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TelemetryService mockTeleService;

    @Test
    void testGetReadings_ValidParamsReturns200() throws Exception {
        when(mockTeleService.query(any(), any(), any(), any()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/api/devices/kauf-01/readings")
                .header("X-API-Key", "test-api-key")
                .param("from", "2024-01-15T00:00:00Z")
                .param("to",   "2024-01-15T06:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetReadings_MissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/api/devices/kauf-01/readings")
                .header("X-API-Key", "test-api-key"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetReadings_NoApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/devices/kauf-01/readings")
                .param("from", "2024-01-15T00:00:00Z")
                .param("to",   "2024-01-15T06:00:00Z"))
            .andExpect(status().isUnauthorized());
    }
}
