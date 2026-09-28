package org.fhirlint.core.rules.duplicate;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.HumanName;
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
 * DUP-002: Identifies distinct Patient resources matching across core demographic attributes:
 * family name, given name, birth date, and postal code (where all four are present and non-blank).
 */
public class PatientDemographicDuplicateRule implements QualityRule {

    public static final String RULE_ID = "DUP-002";
    public static final String RULE_NAME = "PatientDemographicDuplicateRule";
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
        return Severity.WARNING;
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

        Map<String, Set<Patient>> demographicToPatients = new HashMap<>();

        for (IBaseResource resource : dataset) {
            if (resource instanceof Patient patient) {
                String key = extractDemographicKey(patient);
                if (key != null) {
                    demographicToPatients.computeIfAbsent(key, k -> new HashSet<>()).add(patient);
                }
            }
        }

        List<QualityIssue> issues = new ArrayList<>();
        for (Map.Entry<String, Set<Patient>> entry : demographicToPatients.entrySet()) {
            Set<Patient> duplicates = entry.getValue();
            if (duplicates.size() >= 2) {
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
                            .path("Patient")
                            .message("Probable demographic duplicate detected. Multiple Patient resources share matching family name, given name, birthDate, and postal code: [" + conflictingIds + "].")
                            .suggestion("Probable demographic duplicate detected. Multiple Patient resources share matching name, birthDate, and postal code.")
                            .build());
                }
            }
        }

        return issues;
    }

    private String extractDemographicKey(Patient patient) {
        if (!patient.hasName() || !patient.hasBirthDate() || !patient.hasAddress()) {
            return null;
        }

        HumanName name = patient.getNameFirstRep();
        if (name == null || !name.hasFamily() || !name.hasGiven()) {
            return null;
        }

        String family = name.getFamily().trim().toLowerCase();
        String given = name.getGiven().get(0).getValue().trim().toLowerCase();
        if (family.isBlank() || given.isBlank()) {
            return null;
        }

        String birthDate = patient.getBirthDateElement().getValueAsString();
        if (birthDate == null || birthDate.isBlank()) {
            return null;
        }
        birthDate = birthDate.trim();

        Address address = patient.getAddressFirstRep();
        if (address == null || !address.hasPostalCode()) {
            return null;
        }

        String postalCode = address.getPostalCode().trim().toLowerCase();
        if (postalCode.isBlank()) {
            return null;
        }

        // Normalize postal code (e.g. "90210-1234" -> "90210")
        if (postalCode.length() > 5) {
            postalCode = postalCode.substring(0, 5);
        }

        return family + "|" + given + "|" + birthDate + "|" + postalCode;
    }
}
