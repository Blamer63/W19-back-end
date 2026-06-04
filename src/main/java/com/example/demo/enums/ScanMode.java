package com.example.demo.enums;

import java.util.Locale;

public enum ScanMode {
    PRECISION("precision", 2),
    SCENE("scene", 4);

    private final String wireValue;
    private final int maxDetectedObjects;

    ScanMode(String wireValue, int maxDetectedObjects) {
        this.wireValue = wireValue;
        this.maxDetectedObjects = maxDetectedObjects;
    }

    public String getWireValue() {
        return wireValue;
    }

    public int getMaxDetectedObjects() {
        return maxDetectedObjects;
    }

    public static ScanMode fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return PRECISION;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ScanMode mode : values()) {
            if (mode.wireValue.equals(normalized)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Invalid scan_mode. Allowed: precision, scene");
    }
}
