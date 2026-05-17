package com.codedgiraffe.smartvolt.service;

import com.codedgiraffe.smartvolt.dto.DeviceRegistrationRequest;
import com.codedgiraffe.smartvolt.model.Device;
import com.codedgiraffe.smartvolt.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.util.KeyHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {
    
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    // Raw key arrives in the DTO, gets hashed, never stored or logged
    public Device register(DeviceRegistrationRequest request) {
        Device device = new Device();

        device.setDeviceId(request.getDeviceId());
        device.setDeviceKeyHash(KeyHashUtil.hash(request.getDeviceKey()));
        device.setName(request.getName());
        device.setLocation(request.getLocation());

        Device saved = deviceRepository.save(device);
        log.info("Registered device: {}", saved.getDeviceId());
        return saved;
    }

    public void updateOnlineStatus(String deviceId, boolean isOnline) {
        deviceRepository.findByDeviceId(deviceId).ifPresentOrElse(
            device -> {
                device.setOnline(isOnline);
                deviceRepository.save(device);
                log.info("Device {} marked {}", deviceId, isOnline ? "ONLINE" : "OFFLINE");
            },
            () -> log.warn("There was an attempt to update online status for unknown device: {}", deviceId)
        );
    }

    public Optional<Device> findByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId);
    }

    public List<Device> findAll() {
        return deviceRepository.findAll();
    }
}
