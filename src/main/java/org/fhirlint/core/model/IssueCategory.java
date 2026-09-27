package org.fhirlint.core.model;

/**
 * Quality categories evaluated by FHIRLint.
 */
public enum IssueCategory {
    STRUCTURAL("Structural Conformance", 0.20),
    PROFILE_CONFORMANCE("Profile Conformance", 0.20),
    REFERENTIAL_INTEGRITY("Referential Integrity", 0.25),
    CONSISTENCY("Cross-Resource Consistency", 0.15),
    TERMINOLOGY("Terminology Quality", 0.10),
    COMPLETENESS("Data Completeness", 0.10),
    DUPLICATE("Entity Deduplication", 0.00);

    private final String displayName;
    private final double weight;

    IssueCategory(String displayName, double weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getWeight() {
        return weight;
    }
}
