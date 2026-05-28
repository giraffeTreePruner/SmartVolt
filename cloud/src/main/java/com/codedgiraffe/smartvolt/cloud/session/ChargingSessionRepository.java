package com.codedgiraffe.smartvolt.cloud.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, String> {

    Optional<ChargingSession> findByDeviceIdAndStatus(String deviceId, SessionStatus status);

    List<ChargingSession> findByDeviceIdOrderByStartedAtDesc(String deviceId);

    List<ChargingSession> findByDeviceIdAndStatusOrderByStartedAtDesc(String deviceId, SessionStatus status);
}
