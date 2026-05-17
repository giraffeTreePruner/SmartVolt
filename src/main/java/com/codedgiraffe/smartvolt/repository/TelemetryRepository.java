package com.codedgiraffe.smartvolt.repository;

import com.codedgiraffe.smartvolt.model.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TelemetryRepository extends JpaRepository<TelemetryReading, String> {
    Page<TelemetryReading> findByDeviceIdAndTimestampBetween(
        String deviceId, Instant start, Instant end, Pageable pageable
    );
}