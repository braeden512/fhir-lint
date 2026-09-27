package org.fhirlint.core.graph;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * In-memory dataset-level relationship graph index for $O(1)$ reference resolution
 * and topological context analysis.
 */
public class ResourceGraphIndex {

    private final Map<String, ResourceNode> fullUrlIndex = new HashMap<>();
    private final Map<String, ResourceNode> typeAndIdIndex = new HashMap<>();
    private final Map<String, List<ResourceNode>> bareIdIndex = new HashMap<>();
    private final List<ResourceNode> allNodes = new ArrayList<>();

    public ResourceNode indexResource(IBaseResource resource, String fullUrl) {
        ResourceNode node = new ResourceNode(resource, fullUrl);
        allNodes.add(node);

        if (fullUrl != null && !fullUrl.isBlank()) {
            fullUrlIndex.put(fullUrl.trim(), node);
        }

        String qualifiedId = node.getQualifiedId();
        if (qualifiedId != null) {
            typeAndIdIndex.put(qualifiedId, node);
        }

        String id = node.getId();
        if (id != null && !id.isBlank()) {
            bareIdIndex.computeIfAbsent(id, k -> new ArrayList<>()).add(node);
        }

        return node;
    }

    public Optional<ResourceNode> findByFullUrl(String fullUrl) {
        if (fullUrl == null) return Optional.empty();
        return Optional.ofNullable(fullUrlIndex.get(fullUrl.trim()));
    }

    public Optional<ResourceNode> findByTypeAndId(String resourceType, String id) {
        if (resourceType == null || id == null) return Optional.empty();
        return Optional.ofNullable(typeAndIdIndex.get(resourceType + "/" + id));
    }

    public List<ResourceNode> findByBareId(String id) {
        if (id == null) return List.of();
        List<ResourceNode> matches = bareIdIndex.get(id);
        return matches != null ? Collections.unmodifiableList(matches) : List.of();
    }

    public List<ResourceNode> getAllNodes() {
        return Collections.unmodifiableList(allNodes);
    }

    public ReferenceResolution resolve(ResourceReference reference, ResourceNode sourceNode) {
        if (reference == null || reference.referenceType() == ReferenceType.MALFORMED) {
            return ReferenceResolution.malformed();
        }

        String target = reference.targetReference();
        if (target == null || target.isBlank()) {
            return ReferenceResolution.malformed();
        }
        target = target.trim();

        return switch (reference.referenceType()) {
            case CONTAINED_FRAGMENT -> {
                if (sourceNode != null) {
                    IBaseResource contained = sourceNode.getContained(target);
                    if (contained != null) {
                        yield ReferenceResolution.resolved(new ResourceNode(contained, null));
                    }
                }
                yield ReferenceResolution.notFound();
            }
            case URN_UUID, URN_OID -> {
                ResourceNode match = fullUrlIndex.get(target);
                yield match != null ? ReferenceResolution.resolved(match) : ReferenceResolution.notFound();
            }
            case ABSOLUTE_EXTERNAL -> {
                ResourceNode match = fullUrlIndex.get(target);
                if (match != null) {
                    yield ReferenceResolution.resolved(match);
                }
                // Check if absolute URL ends with a known Type/id
                int lastSlash = target.lastIndexOf('/');
                if (lastSlash > 0) {
                    int prevSlash = target.lastIndexOf('/', lastSlash - 1);
                    if (prevSlash >= 0) {
                        String potentialTypeAndId = target.substring(prevSlash + 1);
                        ResourceNode relativeMatch = typeAndIdIndex.get(potentialTypeAndId);
                        if (relativeMatch != null) {
                            yield ReferenceResolution.resolved(relativeMatch);
                        }
                    }
                }
                yield ReferenceResolution.externalUnverified();
            }
            case RELATIVE -> {
                ResourceNode match = typeAndIdIndex.get(target);
                if (match != null) {
                    yield ReferenceResolution.resolved(match);
                }
                // Check if any fullUrl ends with this relative reference
                for (Map.Entry<String, ResourceNode> entry : fullUrlIndex.entrySet()) {
                    if (entry.getKey().endsWith("/" + target)) {
                        yield ReferenceResolution.resolved(entry.getValue());
                    }
                }
                yield ReferenceResolution.notFound();
            }
            case BARE_ID -> {
                List<ResourceNode> candidates = bareIdIndex.get(target);
                if (candidates == null || candidates.isEmpty()) {
                    yield ReferenceResolution.notFound();
                } else if (candidates.size() == 1) {
                    yield ReferenceResolution.resolved(candidates.get(0));
                } else {
                    yield ReferenceResolution.ambiguous(candidates);
                }
            }
            case MALFORMED -> ReferenceResolution.malformed();
        };
    }

    /**
     * Determines whether the given resource node has a direct or indirect path in the
     * relationship graph reaching a Patient node.
     */
    public boolean isReachableToPatient(ResourceNode node) {
        if (node == null) return false;
        if ("Patient".equals(node.getResourceType())) return true;

        Set<ResourceNode> visited = new HashSet<>();
        Queue<ResourceNode> queue = new ArrayDeque<>();
        queue.add(node);
        visited.add(node);

        while (!queue.isEmpty()) {
            ResourceNode current = queue.poll();

            // 1. Check outgoing references
            for (ResourceReference ref : current.getOutgoingReferences()) {
                ReferenceResolution resolution = resolve(ref, current);
                if (resolution.isResolved()) {
                    ResourceNode target = resolution.targetNode();
                    if ("Patient".equals(target.getResourceType())) {
                        return true;
                    }
                    if (visited.add(target)) {
                        queue.add(target);
                    }
                }
            }

            // 2. Check incoming references (e.g. DiagnosticReport -> Observation)
            for (ResourceReference incomingRef : current.getIncomingReferences()) {
                // Find source node that originated this incoming reference
                ResourceNode potentialSource = null;
                if (incomingRef.sourceResourceId() != null) {
                    potentialSource = typeAndIdIndex.get(incomingRef.sourceResourceType() + "/" + incomingRef.sourceResourceId());
                }
                if (potentialSource == null) {
                    for (ResourceNode candidate : allNodes) {
                        if (candidate.getResourceType().equals(incomingRef.sourceResourceType())
                                && Objects.equals(candidate.getId(), incomingRef.sourceResourceId())) {
                            potentialSource = candidate;
                            break;
                        }
                    }
                }
                if (potentialSource != null) {
                    if ("Patient".equals(potentialSource.getResourceType())) {
                        return true;
                    }
                    if (visited.add(potentialSource)) {
                        queue.add(potentialSource);
                    }
                }
            }
        }

        return false;
    }
}
