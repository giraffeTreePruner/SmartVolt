package com.codedgiraffe.smartvolt.controller;

import com.codedgiraffe.smartvolt.dto.DeviceRegistrationRequest;
import com.codedgiraffe.smartvolt.model.Device;
import com.codedgiraffe.smartvolt.service.DeviceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // Getmapping, Postmapping, RequestBody, RequestMapping, RestController

import java.util.List;


@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
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

}
