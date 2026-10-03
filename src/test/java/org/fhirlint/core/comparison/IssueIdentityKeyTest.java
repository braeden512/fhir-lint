package org.fhirlint.core.comparison;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueIdentityKeyTest {

    @Test
    @DisplayName("Produces identical key for two instances of the same issue")
    void testIdenticalIssueKeys() {
        QualityIssue issue1 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-1")
                .path("Observation.subject.reference")
                .message("Patient not found")
                .build();

        QualityIssue issue2 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-1")
                .path("Observation.subject.reference")
                .message("Patient not found")
                .build();

        IssueIdentityKey key1 = IssueIdentityKey.of(issue1);
        IssueIdentityKey key2 = IssueIdentityKey.of(issue2);

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("Distinguishes issues on same resource when path differs")
    void testDifferentPathSameResource() {
        QualityIssue issue1 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-1")
                .path("Observation.subject.reference")
                .message("Patient not found")
                .build();

        QualityIssue issue2 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-1")
                .path("Observation.performer.reference")
                .message("Practitioner not found")
                .build();

        IssueIdentityKey key1 = IssueIdentityKey.of(issue1);
        IssueIdentityKey key2 = IssueIdentityKey.of(issue2);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("Disambiguates un-identified resources using messageHash fallback")
    void testDisambiguateMissingResourceId() {
        QualityIssue issue1 = QualityIssue.builder()
                .ruleId("DUP-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.CONSISTENCY)
                .resourceType("Patient")
                .message("Duplicate SSN 123-45-6789")
                .build();

        QualityIssue issue2 = QualityIssue.builder()
                .ruleId("DUP-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.CONSISTENCY)
                .resourceType("Patient")
                .message("Duplicate SSN 987-65-4321")
                .build();

        IssueIdentityKey key1 = IssueIdentityKey.of(issue1);
        IssueIdentityKey key2 = IssueIdentityKey.of(issue2);

        assertThat(key1.resourceId()).isEqualTo("__NO_ID__");
        assertThat(key2.resourceId()).isEqualTo("__NO_ID__");
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("Throws exception if ruleId or issue is null")
    void testNullValidation() {
        assertThatThrownBy(() -> IssueIdentityKey.of(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new IssueIdentityKey(null, "Patient", "p1", "path", 0))
                .isInstanceOf(NullPointerException.class);
    }
}
