package org.fhirlint.core.rules.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TERM-001: Flags invalid, non-canonical, or misspelled coding system URIs.
 */
public class CanonicalSystemUriRule implements QualityRule {

    public static final String RULE_ID = "TERM-001";
    public static final String RULE_NAME = "CanonicalSystemUriRule";

    private final FhirContext fhirContext;
    private final FhirTerser terser;

    public CanonicalSystemUriRule(FhirContext fhirContext) {
        this.fhirContext = fhirContext != null ? fhirContext : FhirContext.forR4Cached();
        this.terser = this.fhirContext.newTerser();
    }

    public CanonicalSystemUriRule() {
        this(FhirContext.forR4Cached());
    }

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
        return Severity.WARNING;
    }

    @Override
    public RuleScope getScope() {
        return RuleScope.RESOURCE;
    }

    @Override
    public Set<String> getApplicableResourceTypes() {
        return Collections.emptySet(); // Universal across all resource types
    }

    @Override
    public List<QualityIssue> evaluate(RuleContext context) {
        IBaseResource resource = context.getResource().orElse(null);
        TerminologyService terminologyService = context.getTerminologyService();
        if (resource == null || terminologyService == null) {
            return Collections.emptyList();
        }

        List<Coding> codings = terser.getAllPopulatedChildElementsOfType(resource, Coding.class);
        if (codings.isEmpty()) {
            return Collections.emptyList();
        }

        List<QualityIssue> issues = new ArrayList<>();
        for (Coding coding : codings) {
            if (coding.hasSystem()) {
                String system = coding.getSystem();
                if (!terminologyService.isCanonicalSystemUri(system)) {
                    Optional<String> suggestion = terminologyService.getCanonicalSuggestion(system);
                    String suggestionMsg = suggestion
                            .map(s -> "Replace non-canonical system URI with canonical URI: " + s)
                            .orElse("Verify coding system URI against canonical FHIR standards.");

                    String path = resolveCodingPath(resource, coding);

                    issues.add(QualityIssue.builder()
                            .ruleId(RULE_ID)
                            .severity(getDefaultSeverity())
                            .category(getCategory())
                            .resourceType(resource.fhirType())
                            .resourceId(resource.getIdElement().hasIdPart() ? resource.getIdElement().getIdPart() : null)
                            .path(path)
                            .message("Non-canonical or invalid code system URI '" + system + "'.")
                            .suggestion(suggestionMsg)
                            .build());
                }
            }
        }

        return issues;
    }

    private String resolveCodingPath(IBaseResource resource, Coding targetCoding) {
        String base = resource.fhirType();
        if (resource instanceof org.hl7.fhir.r4.model.Observation obs) {
            if (obs.hasCategory() && containsCoding(obs.getCategory(), targetCoding)) {
                return base + ".category.coding.system";
            }
            if (obs.hasCode() && containsCoding(obs.getCode(), targetCoding)) {
                return base + ".code.coding.system";
            }
        } else if (resource instanceof org.hl7.fhir.r4.model.Condition cond) {
            if (cond.hasClinicalStatus() && containsCoding(cond.getClinicalStatus(), targetCoding)) {
                return base + ".clinicalStatus.coding.system";
            }
            if (cond.hasVerificationStatus() && containsCoding(cond.getVerificationStatus(), targetCoding)) {
                return base + ".verificationStatus.coding.system";
            }
            if (cond.hasCode() && containsCoding(cond.getCode(), targetCoding)) {
                return base + ".code.coding.system";
            }
        } else if (resource instanceof org.hl7.fhir.r4.model.MedicationRequest med) {
            if (med.hasMedicationCodeableConcept() && containsCoding(med.getMedicationCodeableConcept(), targetCoding)) {
                return base + ".medicationCodeableConcept.coding.system";
            }
        } else if (resource instanceof org.hl7.fhir.r4.model.Encounter enc) {
            if (enc.hasType() && containsCoding(enc.getType(), targetCoding)) {
                return base + ".type.coding.system";
            }
        }
        return base + ".coding.system";
    }

    private boolean containsCoding(List<org.hl7.fhir.r4.model.CodeableConcept> list, Coding target) {
        if (list == null) return false;
        for (org.hl7.fhir.r4.model.CodeableConcept cc : list) {
            if (containsCoding(cc, target)) return true;
        }
        return false;
    }

    private boolean containsCoding(org.hl7.fhir.r4.model.CodeableConcept cc, Coding target) {
        if (cc == null || !cc.hasCoding()) return false;
        for (Coding c : cc.getCoding()) {
            if (c == target || (java.util.Objects.equals(c.getSystem(), target.getSystem()) && java.util.Objects.equals(c.getCode(), target.getCode()))) {
                return true;
            }
        }
        return false;
    }
}
