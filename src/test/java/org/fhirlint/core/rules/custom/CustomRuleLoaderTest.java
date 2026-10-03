package org.fhirlint.core.rules.custom;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomRuleLoaderTest {

    @Test
    @DisplayName("Should parse valid YAML string and pre-compile FHIRPath rules")
    void shouldParseValidYamlString() {
        String yaml = """
            rules:
              - id: custom-vital-final
                name: "Vital Signs Must Be Final"
                description: "Observations must have status final"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Observation is not final."
                suggestion: "Set status to final."
                
              - id: custom-patient-mrn
                name: "Patient Must Have MRN"
                resourceType: Patient
                category: profileConformance
                severity: warning
                fhirpath: "identifier.exists()"
                message: "Patient missing identifier."
            """;

        List<FhirPathQualityRule> rules = CustomRuleLoader.loadRulesFromString(yaml);
        assertThat(rules).hasSize(2);

        FhirPathQualityRule r1 = rules.get(0);
        assertThat(r1.getRuleId()).isEqualTo("custom-vital-final");
        assertThat(r1.getName()).isEqualTo("Vital Signs Must Be Final");
        assertThat(r1.getCategory()).isEqualTo(IssueCategory.COMPLETENESS);
        assertThat(r1.getDefaultSeverity()).isEqualTo(Severity.ERROR);
        assertThat(r1.getApplicableResourceTypes()).containsExactly("Observation");
        assertThat(r1.getDefinition().suggestion()).isEqualTo("Set status to final.");

        FhirPathQualityRule r2 = rules.get(1);
        assertThat(r2.getRuleId()).isEqualTo("custom-patient-mrn");
        assertThat(r2.getCategory()).isEqualTo(IssueCategory.PROFILE_CONFORMANCE);
        assertThat(r2.getDefaultSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    @DisplayName("Should throw CustomRuleException when duplicate rule IDs exist")
    void shouldFailOnDuplicateRuleId(@TempDir Path tempDir) throws Exception {
        String yaml = """
            rules:
              - id: duplicate-rule
                name: "First"
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "First"
              - id: duplicate-rule
                name: "Second"
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Second"
            """;

        Path file = tempDir.resolve("rules.yaml");
        Files.writeString(file, yaml);

        assertThatThrownBy(() -> CustomRuleLoader.loadRules(file.toString()))
                .isInstanceOf(CustomRuleException.class)
                .hasMessageContaining("Duplicate custom rule ID");
    }

    @Test
    @DisplayName("Should throw CustomRuleException when YAML syntax is malformed")
    void shouldFailOnMalformedYaml() {
        String badYaml = """
            rules:
              - id: bad-yaml
                name: "Broken
                indentation error
            """;

        assertThatThrownBy(() -> CustomRuleLoader.loadRulesFromString(badYaml))
                .isInstanceOf(CustomRuleException.class)
                .hasMessageContaining("Failed parsing custom rules YAML");
    }

    @Test
    @DisplayName("Should throw CustomRuleException when FHIRPath expression has invalid syntax")
    void shouldFailOnInvalidFhirPathSyntax() {
        String badFhirPath = """
            rules:
              - id: bad-syntax
                name: "Bad Syntax"
                category: completeness
                severity: error
                fhirpath: "status = ((unclosed paren"
                message: "Syntax error"
            """;

        assertThatThrownBy(() -> CustomRuleLoader.loadRulesFromString(badFhirPath))
                .isInstanceOf(CustomRuleException.class)
                .hasMessageContaining("Invalid FHIRPath invariant expression");
    }

    @Test
    @DisplayName("Should scan directory and load all .yaml and .yml files")
    void shouldLoadFromDirectory(@TempDir Path tempDir) throws Exception {
        String yaml1 = """
            rules:
              - id: rule-1
                name: "Rule One"
                category: consistency
                severity: info
                fhirpath: "id.exists()"
                message: "Has ID"
            """;

        String yaml2 = """
            rules:
              - id: rule-2
                name: "Rule Two"
                category: terminology
                severity: warning
                fhirpath: "code.exists()"
                message: "Has code"
            """;

        Files.writeString(tempDir.resolve("r1.yaml"), yaml1);
        Files.writeString(tempDir.resolve("r2.yml"), yaml2);

        List<FhirPathQualityRule> rules = CustomRuleLoader.loadRules(tempDir.toString());
        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(FhirPathQualityRule::getRuleId).containsExactlyInAnyOrder("rule-1", "rule-2");
    }
}
