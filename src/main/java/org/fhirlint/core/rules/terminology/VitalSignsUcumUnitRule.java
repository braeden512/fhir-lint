package org.fhirlint.core.rules.terminology;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * TERM-002: Ensures vital signs observations specify valid UCUM units and canonical system "http://unitsofmeasure.org".
 */
public class VitalSignsUcumUnitRule implements QualityRule {

    public static final String RULE_ID = "TERM-002";
    public static final String RULE_NAME = "VitalSignsUcumUnitRule";
    public static final String UCUM_SYSTEM = "http://unitsofmeasure.org";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Observation");

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
        if (!(resource instanceof Observation obs) || terminologyService == null) {
            return Collections.emptyList();
        }

        if (!isVitalSign(obs)) {
            return Collections.emptyList();
        }

        List<QualityIssue> issues = new ArrayList<>();

        if (obs.hasValueQuantity()) {
            validateQuantity(obs.getValueQuantity(), "Observation.valueQuantity", obs, terminologyService, issues);
        }

        if (obs.hasComponent()) {
            for (int i = 0; i < obs.getComponent().size(); i++) {
                Observation.ObservationComponentComponent comp = obs.getComponent().get(i);
                if (comp.hasValueQuantity()) {
                    validateQuantity(comp.getValueQuantity(), "Observation.component[" + i + "].valueQuantity", obs, terminologyService, issues);
                }
            }
        }

        return issues;
    }

    private void validateQuantity(
            Quantity qty,
            String path,
            Observation obs,
            TerminologyService terminologyService,
            List<QualityIssue> issues) {

        boolean systemValid = qty.hasSystem() && UCUM_SYSTEM.equals(qty.getSystem().trim());
        String unitCode = qty.hasCode() ? qty.getCode() : qty.getUnit();
        boolean unitValid = unitCode != null && terminologyService.isValidUcumUnit(unitCode);

        if (!systemValid || !unitValid) {
            issues.add(QualityIssue.builder()
                    .ruleId(RULE_ID)
                    .severity(getDefaultSeverity())
                    .category(getCategory())
                    .resourceType("Observation")
                    .resourceId(obs.getIdElement().hasIdPart() ? obs.getIdElement().getIdPart() : null)
                    .path(path)
                    .message("Vital signs observation valueQuantity requires canonical UCUM unit code and system '" + UCUM_SYSTEM + "'.")
                    .suggestion("Vital sign observations must specify canonical UCUM units with system 'http://unitsofmeasure.org'.")
                    .build());
        }
    }

    private boolean isVitalSign(Observation obs) {
        if (!obs.hasCategory()) {
            return false;
        }
        for (CodeableConcept cc : obs.getCategory()) {
            for (Coding coding : cc.getCoding()) {
                if ("vital-signs".equalsIgnoreCase(coding.getCode())) {
                    return true;
                }
            }
        }
        return false;
    }
}
