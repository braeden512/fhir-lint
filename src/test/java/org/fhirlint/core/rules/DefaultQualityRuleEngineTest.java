package org.fhirlint.core.rules;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.terminology.DefaultTerminologyService;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultQualityRuleEngineTest {

    private RuleRegistry registry;
    private DefaultQualityRuleEngine engine;

    @BeforeEach
    void setUp() {
        registry = new RuleRegistry();
        engine = new DefaultQualityRuleEngine(registry, new DefaultTerminologyService());
    }

    @Test
    void shouldEvaluateDatasetAndResourceRules() {
        // Dataset rule
        registry.register(new QualityRule() {
            @Override public String getRuleId() { return "TEST-DS"; }
            @Override public String getName() { return "Dataset Test"; }
            @Override public IssueCategory getCategory() { return IssueCategory.DUPLICATE; }
            @Override public Severity getDefaultSeverity() { return Severity.ERROR; }
            @Override public RuleScope getScope() { return RuleScope.DATASET; }
            @Override public Set<String> getApplicableResourceTypes() { return Collections.emptySet(); }
            @Override
            public List<QualityIssue> evaluate(RuleContext context) {
                return List.of(QualityIssue.builder()
                        .ruleId("TEST-DS")
                        .severity(Severity.ERROR)
                        .category(IssueCategory.DUPLICATE)
                        .message("Dataset rule evaluated on " + context.getDataset().size() + " resources")
                        .build());
            }
        });

        // Resource rule for Encounter
        registry.register(new QualityRule() {
            @Override public String getRuleId() { return "TEST-RES"; }
            @Override public String getName() { return "Resource Test"; }
            @Override public IssueCategory getCategory() { return IssueCategory.CONSISTENCY; }
            @Override public Severity getDefaultSeverity() { return Severity.WARNING; }
            @Override public RuleScope getScope() { return RuleScope.RESOURCE; }
            @Override public Set<String> getApplicableResourceTypes() { return Set.of("Encounter"); }
            @Override
            public List<QualityIssue> evaluate(RuleContext context) {
                return List.of(QualityIssue.builder()
                        .ruleId("TEST-RES")
                        .severity(Severity.WARNING)
                        .category(IssueCategory.CONSISTENCY)
                        .resourceType(context.getResource().orElseThrow().fhirType())
                        .message("Resource evaluated")
                        .build());
            }
        });

        Patient patient = new Patient();
        patient.setId("pat-1");

        Encounter encounter = new Encounter();
        encounter.setId("enc-1");

        List<QualityIssue> issues = engine.evaluate(List.of(patient, encounter), null);

        assertThat(issues).hasSize(2);
        assertThat(issues).anyMatch(i -> i.ruleId().equals("TEST-DS") && i.message().contains("2 resources"));
        assertThat(issues).anyMatch(i -> i.ruleId().equals("TEST-RES") && i.resourceType().equals("Encounter"));
    }

    @Test
    void shouldReturnEmptyListForEmptyDataset() {
        assertThat(engine.evaluate(Collections.emptyList(), null)).isEmpty();
        assertThat(engine.evaluate(null, null)).isEmpty();
    }
}
