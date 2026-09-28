package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * CONS-001: Ensures period.end occurs at or after period.start on Encounter and Coverage resources.
 */
public class PeriodChronologyRule implements QualityRule {

    public static final String RULE_ID = "CONS-001";
    public static final String RULE_NAME = "PeriodChronologyRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Encounter", "Coverage");

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
        return IssueCategory.CONSISTENCY;
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

        Period period = null;
        if (resource instanceof Encounter encounter && encounter.hasPeriod()) {
            period = encounter.getPeriod();
        } else if (resource instanceof Coverage coverage && coverage.hasPeriod()) {
            period = coverage.getPeriod();
        }

        if (period != null && ChronologyHelper.isPeriodInverted(period)) {
            String startStr = period.getStartElement().getValueAsString();
            String endStr = period.getEndElement().getValueAsString();
            return List.of(QualityIssue.builder()
                    .ruleId(RULE_ID)
                    .severity(getDefaultSeverity())
                    .category(getCategory())
                    .resourceType(resource.fhirType())
                    .resourceId(resource.getIdElement().getIdPart())
                    .path(resource.fhirType() + ".period.end")
                    .message("Period end (" + endStr + ") occurs chronologically before period start (" + startStr + ").")
                    .suggestion("Ensure period.end occurs chronologically at or after period.start.")
                    .build());
        }

        return Collections.emptyList();
    }
}
