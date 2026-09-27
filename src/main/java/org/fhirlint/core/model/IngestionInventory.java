package org.fhirlint.core.model;

import java.util.Collections;
import java.util.Map;

/**
 * Summary of resources discovered during dataset ingestion.
 */
public record IngestionInventory(
    int totalResources,
    Map<String, Integer> resourceTypeCounts,
    long parseDurationMs
) {
    public IngestionInventory {
        resourceTypeCounts = resourceTypeCounts == null 
            ? Collections.emptyMap() 
            : Collections.unmodifiableMap(resourceTypeCounts);
    }
}
