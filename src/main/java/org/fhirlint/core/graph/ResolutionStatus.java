package org.fhirlint.core.graph;

/**
 * Outcome status of attempting to resolve a resource reference within the graph index.
 */
public enum ResolutionStatus {
    /**
     * Reference successfully resolved to a unique target ResourceNode.
     */
    RESOLVED,

    /**
     * Referenced target resource does not exist in the dataset.
     */
    NOT_FOUND,

    /**
     * Referenced bare ID matches multiple candidate resources of different types.
     */
    AMBIGUOUS,

    /**
     * Reference points to an external absolute URI outside the dataset boundary.
     */
    EXTERNAL_UNVERIFIED,

    /**
     * Reference string is empty, whitespace, null, or malformed.
     */
    MALFORMED
}
