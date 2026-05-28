package com.codedgiraffe.smartvolt.edge.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Basic health endpoint for the edge service.
 * Will be enhanced in Phase 2 with buffer depth and MQTT connection status.
 */
@RestController
public class EdgeHealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "smartvolt-edge",
            "timestamp", Instant.now().toString()
        );
    }
}
