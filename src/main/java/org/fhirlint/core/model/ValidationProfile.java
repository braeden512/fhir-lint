package org.fhirlint.core.model;

/**
 * Validation profiles supported by FHIRLint.
 */
public enum ValidationProfile {
    BASE_R4("HL7 FHIR R4 Base Specification"),
    US_CORE("US Core Implementation Guide (v3.1.1+)");

    private final String description;

    ValidationProfile(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ValidationProfile fromString(String value) {
        if (value == null || value.isBlank()) {
            return US_CORE;
        }
        for (ValidationProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(value) || profile.name().replace("_", "").equalsIgnoreCase(value.replace("_", "").replace("-", ""))) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown or unsupported validation profile: '" + value + "'. Supported profiles: BASE_R4, US_CORE.");
    }
}
