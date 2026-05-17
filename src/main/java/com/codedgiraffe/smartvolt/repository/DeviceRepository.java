package com.codedgiraffe.smartvolt.repository;

import com.codedgiraffe.smartvolt.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    Optional<Device> findByDeviceId(String deviceId);
    
}
