package com.codedgiraffe.smartvolt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge acknowledges a command after delivering it to the device.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandAckRequest {
    private String commandId;
    private String status;  // ACKNOWLEDGED, FAILED
}
