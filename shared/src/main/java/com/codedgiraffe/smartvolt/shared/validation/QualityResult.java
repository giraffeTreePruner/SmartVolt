package com.codedgiraffe.smartvolt.shared.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validating a telemetry reading.
 */
@Data
public class QualityResult {
    private final List<QualityFlag> flags = new ArrayList<>();
    private boolean valid = true;

    public void addFlag(QualityFlag flag) {
        flags.add(flag);
        // MISSING_FIELD makes the reading invalid; other flags are warnings
        if (flag == QualityFlag.MISSING_FIELD) {
            valid = false;
        }
    }

    public boolean hasFlag(QualityFlag flag) {
        return flags.contains(flag);
    }
}
