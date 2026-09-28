package org.fhirlint.core.rules;

/**
 * Execution scope for quality rules.
 */
public enum RuleScope {
    /**
     * Rule is evaluated once per individual matching resource.
     */
    RESOURCE,

    /**
     * Rule is evaluated once across the entire aggregate dataset.
     */
    DATASET
}
