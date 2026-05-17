package com.codedgiraffe.smartvolt.dto;

/*
This is the data transfer object (DTO) to register new devices.
~ The structure for API handoffs ~
*/

import jakarta.validation.constraints.NotBlank;

// !USING LOMBOK FOR DATA
import lombok.Data;

@Data
public class DeviceRegistrationRequest {
    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotBlank(message = "Device key is required")
    private String deviceKey;

    private String name;
    private String location;
}
