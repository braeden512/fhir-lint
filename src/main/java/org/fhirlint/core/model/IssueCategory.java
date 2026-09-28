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

    /**
     * Resolves the canonical category under which issues in this category are scored.
     * DUPLICATE issues are scored under CONSISTENCY per FR-013 and ADR-005.
     *
     * @return the canonical scoring category
     */
    public IssueCategory scoringCategory() {
        return this == DUPLICATE ? CONSISTENCY : this;
    }

    /**
     * Indicates whether this category participates directly as an independent scored category.
     * DUPLICATE is not an independent scored category; it maps to CONSISTENCY.
     *
     * @return true if this is one of the 6 canonical scored categories
     */
    public boolean isScored() {
        return this != DUPLICATE;
    }
}
