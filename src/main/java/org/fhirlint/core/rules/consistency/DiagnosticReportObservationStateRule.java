package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.graph.ReferenceResolution;
import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.graph.ResourceNode;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * CONS-004: Flags final DiagnosticReport resources that reference entered-in-error or cancelled observations.
 */
public class DiagnosticReportObservationStateRule implements QualityRule {

    public static final String RULE_ID = "CONS-004";
    public static final String RULE_NAME = "DiagnosticReportObservationStateRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("DiagnosticReport");

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
        if (!(resource instanceof DiagnosticReport report) || context.getGraphIndex() == null) {
            return Collections.emptyList();
        }

        if (report.getStatus() != DiagnosticReport.DiagnosticReportStatus.FINAL) {
            return Collections.emptyList();
        }

        if (!report.hasResult()) {
            return Collections.emptyList();
        }

        List<QualityIssue> issues = new ArrayList<>();
        List<Reference> results = report.getResult();

        for (int i = 0; i < results.size(); i++) {
            Reference ref = results.get(i);
            if (!ref.hasReference()) {
                continue;
            }

            Observation obs = resolveObservation(ref.getReference(), context.getGraphIndex());
            if (obs != null && obs.hasStatus()) {
                Observation.ObservationStatus status = obs.getStatus();
                if (status == Observation.ObservationStatus.ENTEREDINERROR || status == Observation.ObservationStatus.CANCELLED) {
                    String statusStr = status.toCode();
                    String obsId = obs.getIdElement().hasIdPart() ? obs.getIdElement().getIdPart() : ref.getReference();

                    issues.add(QualityIssue.builder()
                            .ruleId(RULE_ID)
                            .severity(getDefaultSeverity())
                            .category(getCategory())
                            .resourceType(report.fhirType())
                            .resourceId(report.getIdElement().getIdPart())
                            .path("DiagnosticReport.result[" + i + "]")
                            .message("Final diagnostic report references an invalid observation (" + obsId + ") with status '" + statusStr + "'.")
                            .suggestion("Final diagnostic reports must not reference entered-in-error or cancelled observations.")
                            .build());
                }
            }
        }

        return issues;
    }

    private Observation resolveObservation(String reference, ResourceGraphIndex graphIndex) {
        return graphIndex.resolveTarget(reference)
                .map(ResourceNode::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .orElse(null);
    }
}
