package com.codedgiraffe.smartvolt.cloud.dto;

import jakarta.validation.constraints.NotBlank;
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
