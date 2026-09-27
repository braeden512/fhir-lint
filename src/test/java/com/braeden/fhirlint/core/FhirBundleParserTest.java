package com.braeden.fhirlint.core;

import com.braeden.fhirlint.core.model.ParsedDataset;
import com.braeden.fhirlint.core.parser.FhirBundleParser;
import com.braeden.fhirlint.core.parser.FhirParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FhirBundleParserTest {

    private FhirBundleParser parser;

    @BeforeEach
    void setUp() {
        parser = new FhirBundleParser();
    }

    @Test
    @DisplayName("Should successfully parse clean-bundle.json and extract inventory")
    void shouldParseCleanBundle() {
        File file = new File("sample-data/clean/clean-bundle.json");
        ParsedDataset dataset = parser.parse(file);

        assertThat(dataset).isNotNull();
        assertThat(dataset.isBundle()).isTrue();
        assertThat(dataset.inventory().totalResources()).isEqualTo(5);
        assertThat(dataset.inventory().resourceTypeCounts())
            .containsEntry("Patient", 1)
            .containsEntry("Encounter", 1)
            .containsEntry("Observation", 1)
            .containsEntry("Condition", 1)
            .containsEntry("MedicationRequest", 1);
    }

    @Test
    @DisplayName("Should successfully parse messy-bundle.json")
    void shouldParseMessyBundle() {
        File file = new File("sample-data/messy/messy-bundle.json");
        ParsedDataset dataset = parser.parse(file);

        assertThat(dataset).isNotNull();
        assertThat(dataset.isBundle()).isTrue();
        assertThat(dataset.inventory().totalResources()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should parse single resource as a single-element dataset")
    void shouldParseSingleResource() {
        String patientJson = """
            {
              "resourceType": "Patient",
              "id": "pat-1",
              "gender": "male"
            }
            """;
        ParsedDataset dataset = parser.parse(patientJson);

        assertThat(dataset.isBundle()).isFalse();
        assertThat(dataset.inventory().totalResources()).isEqualTo(1);
        assertThat(dataset.inventory().resourceTypeCounts()).containsEntry("Patient", 1);
    }

    @Test
    @DisplayName("Should accept empty Bundle without errors")
    void shouldAcceptEmptyBundle() {
        String emptyBundle = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": []
            }
            """;
        ParsedDataset dataset = parser.parse(emptyBundle);

        assertThat(dataset.isBundle()).isTrue();
        assertThat(dataset.inventory().totalResources()).isEqualTo(0);
        assertThat(dataset.inventory().resourceTypeCounts()).isEmpty();
    }

    @Test
    @DisplayName("Should throw FhirParseException on malformed JSON")
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{\"invalid\": "))
            .isInstanceOf(FhirParseException.class)
            .hasMessageContaining("Malformed JSON");
    }

    @Test
    @DisplayName("Should throw FhirParseException when resourceType is missing")
    void shouldRejectMissingResourceType() {
        assertThatThrownBy(() -> parser.parse("{\"id\": \"123\", \"name\": \"test\"}"))
            .isInstanceOf(FhirParseException.class)
            .hasMessageContaining("resourceType");
    }

    @Test
    @DisplayName("Should throw FhirParseException on empty input")
    void shouldRejectEmptyInput() {
        assertThatThrownBy(() -> parser.parse("   "))
            .isInstanceOf(FhirParseException.class)
            .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("Should throw FhirParseException when file does not exist")
    void shouldRejectNonExistentFile() {
        assertThatThrownBy(() -> parser.parse(new File("non-existent-file.json")))
            .isInstanceOf(FhirParseException.class)
            .hasMessageContaining("File not found");
    }
}
