package org.fhirlint.core.graph;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Traverses FHIR R4 resource AST structures to extract all outgoing Reference elements
 * while preserving precise FHIRPaths and property names.
 */
public class ReferenceExtractor {

    public List<ResourceReference> extractReferences(ResourceNode node) {
        List<ResourceReference> references = new ArrayList<>();
        IBaseResource baseResource = node.getResource();

        if (baseResource instanceof DomainResource domainResource) {
            // Index contained sub-resources
            for (Resource contained : domainResource.getContained()) {
                if (contained.getIdElement() != null && contained.getIdElement().hasIdPart()) {
                    node.addContained(contained.getIdElement().getIdPart(), contained);
                }
            }
        }

        if (baseResource instanceof Base base) {
            walk(base, base.fhirType(), base.fhirType(), node.getId(), null, references, node);
        }

        for (ResourceReference ref : references) {
            node.addOutgoingReference(ref);
        }

        return references;
    }

    private void walk(
            Base current,
            String currentPath,
            String resourceType,
            String resourceId,
            String parentPropertyName,
            List<ResourceReference> collector,
            ResourceNode node
    ) {
        if (current == null) {
            return;
        }

        for (Property property : current.children()) {
            String propName = property.getName();

            // Skip contained resources from outer reference path walking
            if ("contained".equals(propName)) {
                continue;
            }

            List<Base> values = property.getValues();
            if (values == null || values.isEmpty()) {
                continue;
            }

            boolean isList = property.isList();
            for (int i = 0; i < values.size(); i++) {
                Base child = values.get(i);
                if (child == null) {
                    continue;
                }

                String childPath = isList 
                        ? currentPath + "." + propName + "[" + i + "]" 
                        : currentPath + "." + propName;

                if (child instanceof Reference ref) {
                    String refString = ref.hasReference() ? ref.getReference() : null;
                    ReferenceType refType = classify(refString);
                    String fullRefPath = childPath + ".reference";

                    ResourceReference resourceRef = new ResourceReference(
                            resourceType,
                            resourceId,
                            fullRefPath,
                            refString,
                            refType,
                            propName
                    );
                    collector.add(resourceRef);
                } else if (!child.isPrimitive()) {
                    walk(child, childPath, resourceType, resourceId, propName, collector, node);
                }
            }
        }
    }

    public static ReferenceType classify(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return ReferenceType.MALFORMED;
        }
        String trimmed = reference.trim();
        if (trimmed.startsWith("#")) {
            return ReferenceType.CONTAINED_FRAGMENT;
        }
        if (trimmed.startsWith("urn:uuid:")) {
            return ReferenceType.URN_UUID;
        }
        if (trimmed.startsWith("urn:oid:")) {
            return ReferenceType.URN_OID;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return ReferenceType.ABSOLUTE_EXTERNAL;
        }
        if (trimmed.contains("/")) {
            return ReferenceType.RELATIVE;
        }
        return ReferenceType.BARE_ID;
    }
}
