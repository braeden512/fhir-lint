package org.fhirlint.core.rules.terminology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTerminologyServiceTest {

    private DefaultTerminologyService terminologyService;

    @BeforeEach
    void setUp() {
        terminologyService = new DefaultTerminologyService();
    }

    @Test
    void shouldRecognizeCanonicalSystemUris() {
        assertThat(terminologyService.isCanonicalSystemUri("http://loinc.org")).isTrue();
        assertThat(terminologyService.isCanonicalSystemUri("http://snomed.info/sct")).isTrue();
        assertThat(terminologyService.isCanonicalSystemUri("http://hl7.org/fhir/sid/rxnorm")).isTrue();
        assertThat(terminologyService.isCanonicalSystemUri("http://hl7.org/fhir/sid/icd-10-cm")).isTrue();
        assertThat(terminologyService.isCanonicalSystemUri("http://unitsofmeasure.org")).isTrue();
    }

    @Test
    void shouldIdentifyNonCanonicalUrisAndSuggestCanonical() {
        assertThat(terminologyService.isCanonicalSystemUri("http://loinc.org/")).isFalse();
        assertThat(terminologyService.getCanonicalSuggestion("http://loinc.org/")).contains("http://loinc.org");

        assertThat(terminologyService.isCanonicalSystemUri("http://snomed.info")).isFalse();
        assertThat(terminologyService.getCanonicalSuggestion("http://snomed.info")).contains("http://snomed.info/sct");

        assertThat(terminologyService.isCanonicalSystemUri("http://www.nlm.nih.gov/research/umls/rxnorm")).isFalse();
        assertThat(terminologyService.getCanonicalSuggestion("http://www.nlm.nih.gov/research/umls/rxnorm")).contains("http://hl7.org/fhir/sid/rxnorm");

        assertThat(terminologyService.isCanonicalSystemUri("http://hl7.org/fhir/sid/icd-10")).isFalse();
        assertThat(terminologyService.getCanonicalSuggestion("http://hl7.org/fhir/sid/icd-10")).contains("http://hl7.org/fhir/sid/icd-10-cm");
    }

    @Test
    void shouldValidateCommonUcumUnits() {
        assertThat(terminologyService.isValidUcumUnit("mm[Hg]")).isTrue();
        assertThat(terminologyService.isValidUcumUnit("kg")).isTrue();
        assertThat(terminologyService.isValidUcumUnit("Cel")).isTrue();
        assertThat(terminologyService.isValidUcumUnit("/min")).isTrue();
        assertThat(terminologyService.isValidUcumUnit("%")).isTrue();

        assertThat(terminologyService.isValidUcumUnit("mmHg")).isFalse();
        assertThat(terminologyService.isValidUcumUnit("degrees C")).isFalse();
        assertThat(terminologyService.isValidUcumUnit("")).isFalse();
        assertThat(terminologyService.isValidUcumUnit(null)).isFalse();
    }

    @Test
    void shouldValidateAdministrativeGender() {
        assertThat(terminologyService.isValidGender("male")).isTrue();
        assertThat(terminologyService.isValidGender("female")).isTrue();
        assertThat(terminologyService.isValidGender("other")).isTrue();
        assertThat(terminologyService.isValidGender("unknown")).isTrue();

        assertThat(terminologyService.isValidGender("M")).isFalse();
        assertThat(terminologyService.isValidGender("invalid")).isFalse();
        assertThat(terminologyService.isValidGender(null)).isFalse();
    }

    @Test
    void shouldValidateEncounterStatus() {
        assertThat(terminologyService.isValidEncounterStatus("planned")).isTrue();
        assertThat(terminologyService.isValidEncounterStatus("in-progress")).isTrue();
        assertThat(terminologyService.isValidEncounterStatus("finished")).isTrue();
        assertThat(terminologyService.isValidEncounterStatus("cancelled")).isTrue();

        assertThat(terminologyService.isValidEncounterStatus("completed")).isFalse();
        assertThat(terminologyService.isValidEncounterStatus(null)).isFalse();
    }

    @Test
    void shouldValidateConditionClinicalStatus() {
        assertThat(terminologyService.isValidConditionClinicalStatus("active")).isTrue();
        assertThat(terminologyService.isValidConditionClinicalStatus("resolved")).isTrue();
        assertThat(terminologyService.isValidConditionClinicalStatus("remission")).isTrue();

        assertThat(terminologyService.isValidConditionClinicalStatus("confirmed")).isFalse();
        assertThat(terminologyService.isValidConditionClinicalStatus(null)).isFalse();
    }
}
