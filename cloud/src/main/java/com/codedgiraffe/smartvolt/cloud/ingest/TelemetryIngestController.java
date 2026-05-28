package com.codedgiraffe.smartvolt.cloud.ingest;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryIngestController {

    private final TelemetryIngestService ingestService;

    public TelemetryIngestController(TelemetryIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/batch")
    public ResponseEntity<TelemetryBatchResponse> ingestBatch(
            @RequestBody TelemetryBatchRequest batch) {
        TelemetryBatchResponse response = ingestService.ingestBatch(batch);
        return ResponseEntity.ok(response);
    }
}
