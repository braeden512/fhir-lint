package org.fhirlint.core.rules.terminology;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * TERM-003: Verifies adherence to required fixed value sets (AdministrativeGender, EncounterStatus, ConditionClinicalStatus).
 */
public class CoreValueSetBindingRule implements QualityRule {

    public static final String RULE_ID = "TERM-003";
    public static final String RULE_NAME = "CoreValueSetBindingRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Patient", "Encounter", "Condition");

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
        return IssueCategory.TERMINOLOGY;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.ERROR;
    }

    @Override
    public RuleScope getScope() {
        return RuleScope.RESOURCE;
    }

    @Override
    public Set<String> getApplicableResourceTypes() {
        return APPLICABLE_TYPES;
    }

    @Override
    public List<QualityIssue> evaluate(RuleContext context) {
        IBaseResource resource = context.getResource().orElse(null);
        TerminologyService terminologyService = context.getTerminologyService();
        if (resource == null || terminologyService == null) {
            return Collections.emptyList();
        }

        List<QualityIssue> issues = new ArrayList<>();

        if (resource instanceof Patient patient && patient.getGenderElement() != null && patient.getGenderElement().hasCode()) {
            String genderCode = patient.getGenderElement().getCode();
            if (genderCode != null && !terminologyService.isValidGender(genderCode)) {
                issues.add(buildIssue(resource, "Patient.gender", genderCode));
            }
        } else if (resource instanceof Encounter encounter && encounter.getStatusElement() != null && encounter.getStatusElement().hasCode()) {
            String statusCode = encounter.getStatusElement().getCode();
            if (statusCode != null && !terminologyService.isValidEncounterStatus(statusCode)) {
                issues.add(buildIssue(resource, "Encounter.status", statusCode));
            }
        } else if (resource instanceof Condition condition && condition.hasClinicalStatus()) {
            for (Coding coding : condition.getClinicalStatus().getCoding()) {
                if (coding.hasCode()) {
                    String statusCode = coding.getCode();
                    if (!terminologyService.isValidConditionClinicalStatus(statusCode)) {
                        issues.add(buildIssue(resource, "Condition.clinicalStatus", statusCode));
                    }
                }
            }
        }

        return issues;
    }

    private QualityIssue buildIssue(IBaseResource resource, String path, String invalidCode) {
        return QualityIssue.builder()
                .ruleId(RULE_ID)
                .severity(getDefaultSeverity())
                .category(getCategory())
                .resourceType(resource.fhirType())
                .resourceId(resource.getIdElement().hasIdPart() ? resource.getIdElement().getIdPart() : null)
                .path(path)
                .message("Value '" + invalidCode + "' is not a permitted code in the required value set for " + path + ".")
                .suggestion("Ensure code conforms to the required fixed value set.")
                .build();
    }
}
