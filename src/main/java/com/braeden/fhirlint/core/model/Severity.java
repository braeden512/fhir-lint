package com.braeden.fhirlint.core.model;

/**
 * Severity level of an identified data quality issue.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO;

    public boolean isAtLeast(Severity threshold) {
        if (threshold == null) {
            return false;
        }
        return this.ordinal() <= threshold.ordinal();
    }
}
