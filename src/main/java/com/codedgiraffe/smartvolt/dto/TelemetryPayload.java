package com.codedgiraffe.smartvolt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryPayload {

    private String key;
    private Double wattage;
    private Double voltage;
    private Double amperage;
    private Double totalKwh;
}
