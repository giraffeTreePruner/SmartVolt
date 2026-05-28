package com.codedgiraffe.smartvolt.cloud.repository;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    Optional<Device> findByDeviceId(String deviceId);
}
