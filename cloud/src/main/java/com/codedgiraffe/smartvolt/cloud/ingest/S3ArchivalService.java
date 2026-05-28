package com.codedgiraffe.smartvolt.cloud.ingest;

import com.codedgiraffe.smartvolt.shared.dto.TelemetryBatchRequest;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class S3ArchivalService {
    private static final Logger log = LoggerFactory.getLogger(S3ArchivalService.class);

    private final ObjectMapper objectMapper;

    @Value("${smartvolt.s3.endpoint:}")
    private String endpoint;

    @Value("${smartvolt.s3.bucket:smartvolt-telemetry-archive}")
    private String bucket;

    @Value("${smartvolt.s3.access-key:}")
    private String accessKey;

    @Value("${smartvolt.s3.secret-key:}")
    private String secretKey;

    @Value("${smartvolt.s3.enabled:false}")
    private boolean enabled;

    private S3Client s3Client;

    public S3ArchivalService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!enabled || endpoint.isBlank()) {
            log.info("S3 archival disabled (smartvolt.s3.enabled=false or no endpoint configured)");
            return;
        }

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();

        log.info("S3 archival enabled: endpoint={}, bucket={}", endpoint, bucket);
    }

    @Async
    public void archiveBatch(TelemetryBatchRequest batch) {
        if (!enabled || s3Client == null) {
            return;
        }

        try {
            String key = buildKey(batch.getDeviceId());
            byte[] json = objectMapper.writeValueAsBytes(batch);

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(json));

            log.debug("Archived batch to s3://{}/{}", bucket, key);
        } catch (Exception e) {
            log.warn("Failed to archive batch for device {}: {}",
                    batch.getDeviceId(), e.getMessage());
        }
    }

    private String buildKey(String deviceId) {
        Instant now = Instant.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")
                .withZone(ZoneOffset.UTC);
        String batchId = UUID.randomUUID().toString().substring(0, 8);
        return String.format("raw/%s/%s-%s.json", deviceId, fmt.format(now), batchId);
    }
}
