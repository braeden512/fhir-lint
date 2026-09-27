package com.braeden.fhirlint.core;

import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.ValidationProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class FhirLinterTest {

    @Test
    @DisplayName("Should successfully lint file using fluent API")
    void shouldLintFileWithFluentApi() {
        FhirLinter linter = FhirLinter.create()
            .withProfile(ValidationProfile.US_CORE);

        File cleanFile = new File("sample-data/clean/clean-bundle.json");
        LintReport report = linter.lint(cleanFile);

        assertThat(report).isNotNull();
        assertThat(report.targetProfile()).isEqualTo(ValidationProfile.US_CORE);
        assertThat(report.inventory().totalResources()).isEqualTo(5);
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.qualityScore().getOverallScore()).isGreaterThanOrEqualTo(90);
        assertThat(report.passes(80, null)).isTrue();
    }
}
