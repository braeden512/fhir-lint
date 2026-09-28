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
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * CONS-003: Flags clinical events occurring after a patient's declared deceasedDateTime.
 */
public class DeceasedStatusRule implements QualityRule {

    public static final String RULE_ID = "CONS-003";
    public static final String RULE_NAME = "DeceasedStatusRule";
    private static final Set<String> APPLICABLE_TYPES = Set.of("Observation", "Procedure", "MedicationRequest", "Encounter");

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
        if (resource == null || context.getGraphIndex() == null) {
            return Collections.emptyList();
        }

        Reference subjectRef = null;
        Date eventDate = null;
        String datePath = null;

        if (resource instanceof Observation obs) {
            subjectRef = obs.getSubject();
            if (obs.hasEffective()) {
                eventDate = ChronologyHelper.extractDate(obs.getEffective());
                datePath = "Observation.effective";
            }
        } else if (resource instanceof Procedure proc) {
            subjectRef = proc.getSubject();
            if (proc.hasPerformed()) {
                eventDate = ChronologyHelper.extractDate(proc.getPerformed());
                datePath = "Procedure.performed";
            }
        } else if (resource instanceof MedicationRequest medReq) {
            subjectRef = medReq.getSubject();
            if (medReq.hasAuthoredOn()) {
                eventDate = medReq.getAuthoredOn();
                datePath = "MedicationRequest.authoredOn";
            }
        } else if (resource instanceof Encounter enc) {
            subjectRef = enc.getSubject();
            if (enc.hasPeriod() && enc.getPeriod().hasStart()) {
                eventDate = enc.getPeriod().getStart();
                datePath = "Encounter.period.start";
            }
        }

        if (subjectRef == null || !subjectRef.hasReference() || eventDate == null) {
            return Collections.emptyList();
        }

        Patient patient = resolvePatient(subjectRef.getReference(), context.getGraphIndex());
        if (patient == null || !patient.hasDeceasedDateTimeType() || !patient.getDeceasedDateTimeType().hasValue()) {
            return Collections.emptyList();
        }

        Date deceasedDate = patient.getDeceasedDateTimeType().getValue();
        if (ChronologyHelper.isAfterDeceased(eventDate, deceasedDate)) {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String eventStr = df.format(eventDate);
            String deceasedStr = df.format(deceasedDate);

            return List.of(QualityIssue.builder()
                    .ruleId(RULE_ID)
                    .severity(getDefaultSeverity())
                    .category(getCategory())
                    .resourceType(resource.fhirType())
                    .resourceId(resource.getIdElement().getIdPart())
                    .path(datePath)
                    .message("Clinical event date (" + eventStr + ") occurs after linked Patient deceased date (" + deceasedStr + ").")
                    .suggestion("Verify clinical event date; clinical activity was recorded after the patient's declared date of death.")
                    .build());
        }

        return Collections.emptyList();
    }

    private Patient resolvePatient(String reference, ResourceGraphIndex graphIndex) {
        return graphIndex.resolveTarget(reference)
                .map(ResourceNode::getResource)
                .filter(Patient.class::isInstance)
                .map(Patient.class::cast)
                .orElse(null);
    }
}
