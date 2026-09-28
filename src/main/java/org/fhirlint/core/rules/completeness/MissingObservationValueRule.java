package org.fhirlint.core.rules.completeness;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Observation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * COMP-002: Flags active Observation resources that contain neither a value[x], child component value,
 * nor a dataAbsentReason.
 */
public class MissingObservationValueRule implements QualityRule {

    public static final String RULE_ID = "COMP-002";
    public static final String RULE_NAME = "MissingObservationValueRule";
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
        return IssueCategory.COMPLETENESS;
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
        return APPLICABLE_TYPES;
    }

    @Override
    public List<QualityIssue> evaluate(RuleContext context) {
        IBaseResource resource = context.getResource().orElse(null);
        if (!(resource instanceof Observation obs)) {
            return Collections.emptyList();
        }

        // Exempt entered-in-error or cancelled observations
        if (obs.hasStatus()) {
            Observation.ObservationStatus status = obs.getStatus();
            if (status == Observation.ObservationStatus.ENTEREDINERROR || status == Observation.ObservationStatus.CANCELLED) {
                return Collections.emptyList();
            }
        }

        // Satisfied if top-level value or dataAbsentReason is present
        if (obs.hasValue() || obs.hasDataAbsentReason()) {
            return Collections.emptyList();
        }

        // Satisfied if any child component has a value or dataAbsentReason (e.g. blood pressure panel)
        if (obs.hasComponent()) {
            for (Observation.ObservationComponentComponent comp : obs.getComponent()) {
                if (comp.hasValue() || comp.hasDataAbsentReason()) {
                    return Collections.emptyList();
                }
            }
        }

        return List.of(QualityIssue.builder()
                .ruleId(RULE_ID)
                .severity(getDefaultSeverity())
                .category(getCategory())
                .resourceType("Observation")
                .resourceId(obs.getIdElement().hasIdPart() ? obs.getIdElement().getIdPart() : null)
                .path("Observation")
                .message("Observation has neither a value nor a dataAbsentReason.")
                .suggestion("Observation has neither a value nor a dataAbsentReason. Document clinical findings or explain absence reason.")
                .build());
    }
}
