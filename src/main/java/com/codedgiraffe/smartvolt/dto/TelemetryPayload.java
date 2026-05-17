package com.codedgiraffe.smartvolt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryPayload {

    @JsonProperty("key")
    private String key;

    @JsonProperty("ENERGY")
    private EnergyData energy;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnergyData {
        @JsonProperty("Power")   private Double wattage;
        @JsonProperty("Voltage") private Double voltage;
        @JsonProperty("Current") private Double amperage;
        @JsonProperty("Today")   private Double totalKwh;
    }
}