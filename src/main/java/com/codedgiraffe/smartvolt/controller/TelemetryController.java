package com.codedgiraffe.smartvolt.controller;

import com.codedgiraffe.smartvolt.model.TelemetryReading;
import com.codedgiraffe.smartvolt.service.TelemetryService;
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
