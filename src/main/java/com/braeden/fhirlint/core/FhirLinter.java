package com.braeden.fhirlint.core;

import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.ParsedDataset;
import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.QualityScore;
import com.braeden.fhirlint.core.model.ValidationProfile;
import com.braeden.fhirlint.core.parser.FhirBundleParser;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Main programmatic entry point for FHIRLint.
 * Provides a fluent Java API for embedding in data pipelines and applications.
 */
public class FhirLinter {

    private final FhirBundleParser parser;
    private final com.braeden.fhirlint.core.validation.FhirValidationEngine validationEngine;
    private ValidationProfile profile = ValidationProfile.US_CORE;

    private FhirLinter() {
        this.parser = new FhirBundleParser();
        this.validationEngine = new com.braeden.fhirlint.core.validation.FhirValidationEngine();
    }

    public static FhirLinter create() {
        return new FhirLinter();
    }

    public FhirLinter withProfile(ValidationProfile profile) {
        if (profile != null) {
            this.profile = profile;
        }
        return this;
    }

    public ValidationProfile getProfile() {
        return profile;
    }

    public FhirBundleParser getParser() {
        return parser;
    }

    public com.braeden.fhirlint.core.validation.FhirValidationEngine getValidationEngine() {
        return validationEngine;
    }

    /**
     * Lints a FHIR JSON file.
     */
    public LintReport lint(File file) {
        long start = System.currentTimeMillis();
        ParsedDataset dataset = parser.parse(file);
        return evaluate(dataset, start);
    }

    /**
     * Lints a FHIR JSON string.
     */
    public LintReport lint(String jsonContent) {
        long start = System.currentTimeMillis();
        ParsedDataset dataset = parser.parse(jsonContent);
        return evaluate(dataset, start);
    }

    /**
     * Lints a FHIR JSON InputStream.
     */
    public LintReport lint(InputStream inputStream) {
        long start = System.currentTimeMillis();
        ParsedDataset dataset = parser.parse(inputStream);
        return evaluate(dataset, start);
    }

    /**
     * Lints multiple FHIR JSON files, aggregating their resources into a unified report.
     */
    public LintReport lint(List<File> files) {
        long start = System.currentTimeMillis();
        int totalResources = 0;
        java.util.Map<String, Integer> aggregatedCounts = new java.util.TreeMap<>();
        List<QualityIssue> allIssues = new ArrayList<>();

        for (File file : files) {
            ParsedDataset dataset = parser.parse(file);
            totalResources += dataset.inventory().totalResources();
            for (java.util.Map.Entry<String, Integer> entry : dataset.inventory().resourceTypeCounts().entrySet()) {
                aggregatedCounts.put(entry.getKey(), aggregatedCounts.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
            if (!dataset.parseMessages().isEmpty()) {
                allIssues.addAll(validationEngine.getNormalizer().normalize(dataset.parseMessages(), null, null));
            }
            allIssues.addAll(validationEngine.validateAll(dataset.resources(), profile));
        }

        long durationMs = System.currentTimeMillis() - start;
        com.braeden.fhirlint.core.model.IngestionInventory inventory = 
            new com.braeden.fhirlint.core.model.IngestionInventory(totalResources, aggregatedCounts, durationMs);
        QualityScore score = QualityScore.calculate(totalResources, allIssues);

        return new LintReport(profile, inventory, allIssues, score, durationMs);
    }

    private LintReport evaluate(ParsedDataset dataset, long startTime) {
        List<QualityIssue> issues = new ArrayList<>();
        if (!dataset.parseMessages().isEmpty()) {
            issues.addAll(validationEngine.getNormalizer().normalize(dataset.parseMessages(), null, null));
        }
        issues.addAll(validationEngine.validateAll(dataset.resources(), profile));
        QualityScore score = QualityScore.calculate(dataset.inventory().totalResources(), issues);

        long durationMs = System.currentTimeMillis() - startTime;
        return new LintReport(
            profile,
            dataset.inventory(),
            issues,
            score,
            durationMs
        );
    }
}
