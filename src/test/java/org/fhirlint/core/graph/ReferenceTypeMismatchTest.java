package org.fhirlint.core.graph;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.parser.FhirBundleParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTypeMismatchTest {

    private ReferentialIntegrityEngine engine;
    private FhirBundleParser parser;

    @BeforeEach
    void setUp() {
        engine = ReferentialIntegrityEngine.create();
        parser = new FhirBundleParser();
    }

    @Test
    @DisplayName("Should detect reference target type mismatch in type-mismatch fixture")
    void shouldDetectTypeMismatchInFixture() {
        File file = new File("sample-data/referential/type-mismatch.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref002Issues = issues.stream()
                .filter(i -> "REF-002".equals(i.ruleId()))
                .toList();

        assertThat(ref002Issues).hasSize(1);
        QualityIssue mismatch = ref002Issues.get(0);
        assertThat(mismatch.severity()).isEqualTo(Severity.ERROR);
        assertThat(mismatch.category()).isEqualTo(IssueCategory.REFERENTIAL_INTEGRITY);
        assertThat(mismatch.path()).contains("Observation.subject.reference");
        assertThat(mismatch.message()).contains("Target type 'Condition' is not valid for Observation.subject");
        assertThat(mismatch.message()).contains("Patient");
        assertThat(mismatch.suggestion()).contains("Update the reference to point to a valid target resource type");
    }

    @Test
    @DisplayName("Should pass when reference target type conforms to schema expectations")
    void shouldPassOnValidTargetType() {
        File file = new File("sample-data/clean/clean-bundle.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref002Issues = issues.stream()
                .filter(i -> "REF-002".equals(i.ruleId()))
                .toList();

        assertThat(ref002Issues).isEmpty();
    }

    @Test
    @DisplayName("Should resolve permitted types for nested backbone element references")
    void shouldResolvePermittedTypesForNestedBackboneElements() {
        DefaultReferentialIntegrityEngine defaultEngine = (DefaultReferentialIntegrityEngine) engine;
        java.util.Set<String> permitted = defaultEngine.getPermittedTargetTypes(
                "Encounter",
                "Encounter.participant[0].individual.reference",
                "individual"
        );

        assertThat(permitted).isNotEmpty();
        assertThat(permitted).contains("Practitioner");
        assertThat(permitted).doesNotContain("Condition");
    }

    @Test
    @DisplayName("Should detect REF-002 type mismatch inside nested backbone element")
    void shouldDetectMismatchInNestedBackboneElement() {
        String json = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Condition",
                    "id": "cond-1",
                    "subject": { "reference": "Patient/pat-1" }
                  }
                },
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-1"
                  }
                },
                {
                  "resource": {
                    "resourceType": "Encounter",
                    "id": "enc-1",
                    "status": "finished",
                    "class": { "code": "AMB" },
                    "participant": [
                      {
                        "individual": {
                          "reference": "Condition/cond-1"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;

        List<IBaseResource> resources = parser.parse(json).resources();
        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref002Issues = issues.stream()
                .filter(i -> "REF-002".equals(i.ruleId()))
                .toList();

        assertThat(ref002Issues).hasSize(1);
        QualityIssue issue = ref002Issues.get(0);
        assertThat(issue.path()).contains("Encounter.participant[0].individual.reference");
        assertThat(issue.message()).contains("Target type 'Condition' is not valid for Encounter.individual");
    }
}
