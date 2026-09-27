package org.fhirlint.core.graph;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the resolution outcome of an individual ResourceReference.
 */
public record ReferenceResolution(
    ResolutionStatus status,
    ResourceNode targetNode,
    List<ResourceNode> ambiguousCandidates
) {
    public ReferenceResolution {
        Objects.requireNonNull(status, "status must not be null");
        if (ambiguousCandidates == null) {
            ambiguousCandidates = List.of();
        } else {
            ambiguousCandidates = Collections.unmodifiableList(ambiguousCandidates);
        }
    }

    public static ReferenceResolution resolved(ResourceNode targetNode) {
        Objects.requireNonNull(targetNode, "targetNode must not be null for RESOLVED status");
        return new ReferenceResolution(ResolutionStatus.RESOLVED, targetNode, List.of());
    }

    public static ReferenceResolution notFound() {
        return new ReferenceResolution(ResolutionStatus.NOT_FOUND, null, List.of());
    }

    public static ReferenceResolution ambiguous(List<ResourceNode> candidates) {
        return new ReferenceResolution(ResolutionStatus.AMBIGUOUS, null, candidates);
    }

    public static ReferenceResolution externalUnverified() {
        return new ReferenceResolution(ResolutionStatus.EXTERNAL_UNVERIFIED, null, List.of());
    }

    public static ReferenceResolution malformed() {
        return new ReferenceResolution(ResolutionStatus.MALFORMED, null, List.of());
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED;
    }
}
