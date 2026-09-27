package org.fhirlint.core.graph;

import java.util.Objects;

/**
 * Represents an individual directed reference extracted from a FHIR resource element.
 */
public record ResourceReference(
    String sourceResourceType,
    String sourceResourceId,
    String sourcePath,
    String targetReference,
    ReferenceType referenceType,
    String propertyName
) {
    public ResourceReference {
        Objects.requireNonNull(sourceResourceType, "sourceResourceType must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(referenceType, "referenceType must not be null");
    }
}
