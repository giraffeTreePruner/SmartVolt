package com.codedgiraffe.smartvolt.cloud.controller;

import com.codedgiraffe.smartvolt.cloud.dto.DeviceRegistrationRequest;
import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.mqtt.CommandPublisher;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;
    private final CommandPublisher commandPublisher;

    public DeviceController(DeviceService deviceService, CommandPublisher commandPublisher) {
        this.deviceService = deviceService;
        this.commandPublisher = commandPublisher;
    }

    @PostMapping
    public ResponseEntity<Device> register(
            @Valid @RequestBody DeviceRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(deviceService.register(request));
    }

    @GetMapping
    public List<Device> listAll() {
        return deviceService.findAll();
    }

    @PostMapping("/{deviceId}/power")
    public ResponseEntity<Map<String, String>> setPower(
            @PathVariable String deviceId,
            @RequestParam boolean on) {
        deviceService.findByDeviceId(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        commandPublisher.setPower(deviceId, on);
        return ResponseEntity.ok(Map.of(
            "deviceId", deviceId,
            "power", on ? "ON" : "OFF"));
    }
}
