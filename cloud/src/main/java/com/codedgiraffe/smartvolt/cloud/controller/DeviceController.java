package com.codedgiraffe.smartvolt.cloud.controller;

import com.codedgiraffe.smartvolt.cloud.command.CommandService;
import com.codedgiraffe.smartvolt.cloud.command.CommandType;
import com.codedgiraffe.smartvolt.cloud.dto.DeviceRegistrationRequest;
import com.codedgiraffe.smartvolt.cloud.model.Device;
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
    private final CommandService commandService;

    public DeviceController(DeviceService deviceService, CommandService commandService) {
        this.deviceService = deviceService;
        this.commandService = commandService;
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
        commandService.createCommand(deviceId, on ? CommandType.POWER_ON : CommandType.POWER_OFF);
        return ResponseEntity.ok(Map.of(
            "deviceId", deviceId,
            "power", on ? "ON" : "OFF"));
    }
}
