package com.codedgiraffe.smartvolt.cloud.repository;

import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelemetryRepository extends JpaRepository<TelemetryReading, String> {
    Page<TelemetryReading> findByDeviceIdAndTimestampBetween(
        String deviceId, Instant start, Instant end, Pageable pageable
    );

    Optional<TelemetryReading> findTopByDeviceIdOrderByTimestampDesc(String deviceId);

    List<TelemetryReading> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
        String deviceId, Instant start, Instant end
    );
}
