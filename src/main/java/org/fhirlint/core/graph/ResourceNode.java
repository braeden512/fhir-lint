package org.fhirlint.core.graph;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An in-memory representation of an individual FHIR resource in the relationship graph.
 */
public class ResourceNode {

    private final IBaseResource resource;
    private final String resourceType;
    private final String id;
    private final String fullUrl;
    private final Map<String, IBaseResource> containedIndex = new LinkedHashMap<>();
    private final List<ResourceReference> outgoingReferences = new ArrayList<>();
    private final List<ResourceReference> incomingReferences = new ArrayList<>();

    public ResourceNode(IBaseResource resource, String fullUrl) {
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        this.resourceType = resource.fhirType();
        this.id = resource.getIdElement() != null && resource.getIdElement().hasIdPart() 
                ? resource.getIdElement().getIdPart() 
                : null;
        this.fullUrl = fullUrl;
    }

    public IBaseResource getResource() {
        return resource;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getId() {
        return id;
    }

    public String getFullUrl() {
        return fullUrl;
    }

    public Map<String, IBaseResource> getContainedIndex() {
        return Collections.unmodifiableMap(containedIndex);
    }

    public void addContained(String fragmentId, IBaseResource containedResource) {
        if (fragmentId != null && containedResource != null) {
            String cleanId = fragmentId.startsWith("#") ? fragmentId.substring(1) : fragmentId;
            containedIndex.put(cleanId, containedResource);
        }
    }

    public IBaseResource getContained(String fragmentId) {
        if (fragmentId == null) return null;
        String cleanId = fragmentId.startsWith("#") ? fragmentId.substring(1) : fragmentId;
        return containedIndex.get(cleanId);
    }

    public List<ResourceReference> getOutgoingReferences() {
        return Collections.unmodifiableList(outgoingReferences);
    }

    public void addOutgoingReference(ResourceReference ref) {
        if (ref != null) {
            outgoingReferences.add(ref);
        }
    }

    public List<ResourceReference> getIncomingReferences() {
        return Collections.unmodifiableList(incomingReferences);
    }

    public void addIncomingReference(ResourceReference ref) {
        if (ref != null) {
            incomingReferences.add(ref);
        }
    }

    public String getQualifiedId() {
        if (id != null) {
            return resourceType + "/" + id;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ResourceNode{" +
                "type='" + resourceType + '\'' +
                ", id='" + id + '\'' +
                ", fullUrl='" + fullUrl + '\'' +
                '}';
    }
}
