package com.codedgiraffe.smartvolt.cloud.controller;

import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.service.TelemetryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/devices")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService teleServ) {
        this.telemetryService = teleServ;
    }

    @GetMapping("/{deviceId}/readings")
    public Page<TelemetryReading> queryTelemetry(
            @PathVariable String deviceId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @PageableDefault(size = 100, sort = "timestamp") Pageable pageable) {
        return telemetryService.query(deviceId, from, to, pageable);
    }
}
