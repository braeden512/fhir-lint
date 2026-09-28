package org.fhirlint.core.graph;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default in-memory implementation of ReferentialIntegrityEngine.
 * Performs multi-pass index construction, edge resolution, schema type validation,
 * and topological context analysis.
 */
public class DefaultReferentialIntegrityEngine implements ReferentialIntegrityEngine {

    private final FhirContext fhirContext;
    private final ReferenceExtractor referenceExtractor;

    public DefaultReferentialIntegrityEngine() {
        this(FhirContext.forR4Cached());
    }

    public DefaultReferentialIntegrityEngine(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
        this.referenceExtractor = new ReferenceExtractor();
    }

    @Override
    public List<QualityIssue> analyze(Bundle bundle) {
        if (bundle == null) return List.of();
        return analyze(Collections.singletonList(bundle));
    }

    @Override
    public List<QualityIssue> analyze(List<? extends IBaseResource> resources) {
        if (resources == null || resources.isEmpty()) return List.of();
        ResourceGraphIndex index = buildIndex(resources);
        return analyze(index);
    }

    @Override
    public List<QualityIssue> analyze(ResourceGraphIndex index) {
        if (index == null) return List.of();
        List<QualityIssue> issues = new ArrayList<>();

        // Rule Evaluation Pass
        for (ResourceNode node : index.getAllNodes()) {
            // 1. Evaluate outgoing references (REF-001, REF-002, REF-004)
            for (ResourceReference ref : node.getOutgoingReferences()) {
                ReferenceResolution resolution = index.resolve(ref, node);
                switch (resolution.status()) {
                    case NOT_FOUND -> issues.add(QualityIssue.builder()
                            .severity(Severity.ERROR)
                            .category(IssueCategory.REFERENTIAL_INTEGRITY)
                            .ruleId("REF-001")
                            .resourceType(node.getResourceType())
                            .resourceId(node.getId())
                            .path(ref.sourcePath())
                            .message("Referenced target '" + ref.targetReference() + "' does not exist in dataset.")
                            .suggestion("Verify that the referenced resource is included in the dataset or update the reference ID.")
                            .build());
                    case AMBIGUOUS -> issues.add(QualityIssue.builder()
                            .severity(Severity.ERROR)
                            .category(IssueCategory.REFERENTIAL_INTEGRITY)
                            .ruleId("REF-001")
                            .resourceType(node.getResourceType())
                            .resourceId(node.getId())
                            .path(ref.sourcePath())
                            .message("Ambiguous reference '" + ref.targetReference() + "' matches multiple resources of different types.")
                            .suggestion("Prefix the reference with the target resource type (e.g. 'Patient/" + ref.targetReference() + "').")
                            .build());
                    case MALFORMED -> issues.add(QualityIssue.builder()
                            .severity(Severity.ERROR)
                            .category(IssueCategory.REFERENTIAL_INTEGRITY)
                            .ruleId("REF-001")
                            .resourceType(node.getResourceType())
                            .resourceId(node.getId())
                            .path(ref.sourcePath())
                            .message("Reference value is empty, whitespace, or malformed.")
                            .suggestion("Provide a valid non-empty reference string (e.g. 'Patient/123').")
                            .build());
                    case EXTERNAL_UNVERIFIED -> issues.add(QualityIssue.builder()
                            .severity(Severity.INFO)
                            .category(IssueCategory.REFERENTIAL_INTEGRITY)
                            .ruleId("REF-004")
                            .resourceType(node.getResourceType())
                            .resourceId(node.getId())
                            .path(ref.sourcePath())
                            .message("Reference points to an external absolute URI outside the dataset boundary: '" + ref.targetReference() + "'.")
                            .suggestion("Ensure external endpoint connectivity and availability in downstream environments.")
                            .build());
                    case RESOLVED -> {
                        // REF-002: Target type validation
                        ResourceNode targetNode = resolution.targetNode();
                        Set<String> permittedTypes = getPermittedTargetTypes(node.getResourceType(), ref.sourcePath(), ref.propertyName());
                        if (!permittedTypes.isEmpty() && !permittedTypes.contains(targetNode.getResourceType()) && !permittedTypes.contains("Resource")) {
                            issues.add(QualityIssue.builder()
                                    .severity(Severity.ERROR)
                                    .category(IssueCategory.REFERENTIAL_INTEGRITY)
                                    .ruleId("REF-002")
                                    .resourceType(node.getResourceType())
                                    .resourceId(node.getId())
                                    .path(ref.sourcePath())
                                    .message("Target type '" + targetNode.getResourceType() + "' is not valid for " 
                                            + node.getResourceType() + "." + ref.propertyName() + ". Expected: " 
                                            + String.join(", ", permittedTypes) + ".")
                                    .suggestion("Update the reference to point to a valid target resource type (" 
                                            + String.join(", ", permittedTypes) + ").")
                                    .build());
                        }
                    }
                }
            }

            // 2. Evaluate orphan status (REF-003)
            String type = node.getResourceType();
            if ("Observation".equals(type) || "Condition".equals(type) || "DiagnosticReport".equals(type)) {
                if (!index.isReachableToPatient(node)) {
                    issues.add(QualityIssue.builder()
                            .severity(Severity.WARNING)
                            .category(IssueCategory.REFERENTIAL_INTEGRITY)
                            .ruleId("REF-003")
                            .resourceType(node.getResourceType())
                            .resourceId(node.getId())
                            .path(node.getResourceType() + (node.getId() != null ? "/" + node.getId() : ""))
                            .message("Orphaned clinical resource lacks direct or indirect context link to a Patient.")
                            .suggestion("Link this " + node.getResourceType() + " to a Patient or an Encounter with a valid patient subject.")
                            .build());
                }
            }
        }

        return issues;
    }

    @Override
    public ResourceGraphIndex buildIndex(List<? extends IBaseResource> resources) {
        ResourceGraphIndex index = new ResourceGraphIndex();
        if (resources == null || resources.isEmpty()) {
            return index;
        }

        for (IBaseResource item : resources) {
            if (item instanceof Bundle bundle) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.hasResource()) {
                        index.indexResource(entry.getResource(), entry.getFullUrl());
                    }
                }
            } else {
                index.indexResource(item, null);
            }
        }

        // Extract references for all indexed nodes
        for (ResourceNode node : index.getAllNodes()) {
            referenceExtractor.extractReferences(node);
        }

        // Populate incoming references on resolved target nodes
        for (ResourceNode node : index.getAllNodes()) {
            for (ResourceReference ref : node.getOutgoingReferences()) {
                ReferenceResolution resolution = index.resolve(ref, node);
                if (resolution.isResolved()) {
                    resolution.targetNode().addIncomingReference(ref);
                }
            }
        }

        return index;
    }

    public Set<String> getPermittedTargetTypes(String resourceType, String propertyName) {
        return getPermittedTargetTypes(resourceType, null, propertyName);
    }

    public Set<String> getPermittedTargetTypes(String resourceType, String sourcePath, String propertyName) {
        if (resourceType == null) {
            return Collections.emptySet();
        }

        try {
            BaseRuntimeChildDefinition childDef = null;

            // Attempt to resolve nested path if sourcePath is present
            if (sourcePath != null && sourcePath.contains(".")) {
                String relativePath = sourcePath;
                if (relativePath.startsWith(resourceType + ".")) {
                    relativePath = relativePath.substring(resourceType.length() + 1);
                }
                if (relativePath.endsWith(".reference")) {
                    relativePath = relativePath.substring(0, relativePath.length() - ".reference".length());
                }

                // Remove array indices like [0]
                relativePath = relativePath.replaceAll("\\[\\d+\\]", "");
                String[] segments = relativePath.split("\\.");

                ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition<?> currentComposite = 
                        fhirContext.getResourceDefinition(resourceType);

                for (int i = 0; i < segments.length; i++) {
                    String segment = segments[i];
                    if (currentComposite == null) {
                        break;
                    }
                    BaseRuntimeChildDefinition currentChild = currentComposite.getChildByName(segment);
                    if (currentChild == null) {
                        break;
                    }
                    if (i == segments.length - 1) {
                        childDef = currentChild;
                    } else {
                        BaseRuntimeElementDefinition<?> childElem = currentChild.getChildByName(segment);
                        if (childElem instanceof ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition<?> comp) {
                            currentComposite = comp;
                        } else {
                            break;
                        }
                    }
                }
            }

            // Fallback to direct child on resource definition
            if (childDef == null && propertyName != null) {
                RuntimeResourceDefinition resourceDef = fhirContext.getResourceDefinition(resourceType);
                childDef = resourceDef.getChildByName(propertyName);
            }

            if (childDef == null) {
                return Collections.emptySet();
            }

            List<Class<? extends IBaseResource>> types = null;
            if (childDef instanceof ca.uhn.fhir.context.RuntimeChildResourceDefinition resourceChildDef) {
                types = resourceChildDef.getResourceTypes();
            } else if (childDef instanceof ca.uhn.fhir.context.RuntimeChildChoiceDefinition choiceChildDef) {
                types = choiceChildDef.getResourceTypes();
            }

            if (types != null && !types.isEmpty()) {
                Set<String> permittedTypes = new HashSet<>();
                for (Class<? extends IBaseResource> clazz : types) {
                    RuntimeResourceDefinition targetDef = fhirContext.getResourceDefinition(clazz);
                    permittedTypes.add(targetDef.getName());
                }
                return permittedTypes;
            }
            return Collections.emptySet();
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
