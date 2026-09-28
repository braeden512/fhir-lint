package org.fhirlint.core.rules.completeness;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * COMP-001: Flags clinical resources (Observation, Condition) missing mandatory subject context.
 */
public class MissingSubjectContextRule implements QualityRule {

    public static final String RULE_ID = "COMP-001";
    public static final String RULE_NAME = "MissingSubjectContextRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Observation", "Condition");

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
        if (resource == null) {
            return Collections.emptyList();
        }

        Reference subjectRef = null;
        if (resource instanceof Observation obs) {
            subjectRef = obs.getSubject();
        } else if (resource instanceof Condition cond) {
            subjectRef = cond.getSubject();
        }

        boolean hasValidSubject = subjectRef != null && subjectRef.hasReference() && !subjectRef.getReference().isBlank();
        if (!hasValidSubject) {
            return List.of(QualityIssue.builder()
                    .ruleId(RULE_ID)
                    .severity(getDefaultSeverity())
                    .category(getCategory())
                    .resourceType(resource.fhirType())
                    .resourceId(resource.getIdElement().hasIdPart() ? resource.getIdElement().getIdPart() : null)
                    .path(resource.fhirType() + ".subject")
                    .message("Clinical resource (" + resource.fhirType() + ") is missing mandatory subject reference.")
                    .suggestion("Clinical resource must declare a subject reference linking it to a Patient.")
                    .build());
        }

        return Collections.emptyList();
    }
}
