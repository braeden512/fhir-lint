package org.fhirlint.core.rules.duplicate;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DUP-001: Identifies distinct Patient resources in the dataset sharing the same identifier system and value.
 */
public class PatientIdentifierDuplicateRule implements QualityRule {

    public static final String RULE_ID = "DUP-001";
    public static final String RULE_NAME = "PatientIdentifierDuplicateRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Patient");

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public IssueCategory getCategory() {
        return IssueCategory.DUPLICATE;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.ERROR;
    }

    @Override
    public RuleScope getScope() {
        return RuleScope.DATASET;
    }

    @Override
    public Set<String> getApplicableResourceTypes() {
        return APPLICABLE_TYPES;
    }

    @Override
    public List<QualityIssue> evaluate(RuleContext context) {
        List<IBaseResource> dataset = context.getDataset();
        if (dataset == null || dataset.isEmpty()) {
            return Collections.emptyList();
        }

        // Map identifier compound key -> list of distinct patients with this identifier
        Map<String, Set<Patient>> identifierToPatients = new HashMap<>();

        for (IBaseResource resource : dataset) {
            if (resource instanceof Patient patient && patient.hasIdentifier()) {
                for (Identifier identifier : patient.getIdentifier()) {
                    if (identifier.hasSystem() && identifier.hasValue() 
                            && !identifier.getSystem().isBlank() && !identifier.getValue().isBlank()) {
                        String key = identifier.getSystem().trim() + "|" + identifier.getValue().trim();
                        identifierToPatients.computeIfAbsent(key, k -> new HashSet<>()).add(patient);
                    }
                }
            }
        }

        List<QualityIssue> issues = new ArrayList<>();
        // For keys with >= 2 distinct patients, report duplicate finding for each patient
        for (Map.Entry<String, Set<Patient>> entry : identifierToPatients.entrySet()) {
            Set<Patient> duplicates = entry.getValue();
            if (duplicates.size() >= 2) {
                String key = entry.getKey();
                String conflictingIds = duplicates.stream()
                        .map(p -> p.getIdElement().hasIdPart() ? p.getIdElement().getIdPart() : "unknown")
                        .sorted()
                        .collect(Collectors.joining(", "));

                for (Patient patient : duplicates) {
                    issues.add(QualityIssue.builder()
                            .ruleId(RULE_ID)
                            .severity(getDefaultSeverity())
                            .category(getCategory())
                            .resourceType("Patient")
                            .resourceId(patient.getIdElement().hasIdPart() ? patient.getIdElement().getIdPart() : null)
                            .path("Patient.identifier")
                            .message("Distinct Patient resources share the same identifier (" + key + "): [" + conflictingIds + "].")
                            .suggestion("Multiple Patient resources share the same identifier system and value. Reconcile or deduplicate patient identities.")
                            .build());
                }
            }
        }

        return issues;
    }
}
