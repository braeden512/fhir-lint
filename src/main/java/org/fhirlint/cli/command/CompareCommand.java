package org.fhirlint.cli.command;

import org.fhirlint.cli.renderer.ComparisonJsonRenderer;
import org.fhirlint.cli.renderer.ComparisonTableRenderer;
import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.comparison.ComparisonGateResult;
import org.fhirlint.core.comparison.ComparisonReport;
import org.fhirlint.core.comparison.DatasetComparator;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.ValidationProfile;
import org.fhirlint.core.parser.FhirParseException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command to compare two FHIR datasets and detect quality regressions.
 */
@Command(
    name = "compare",
    description = "Compare two FHIR datasets, evaluate quality score deltas, and detect regressions.",
    mixinStandardHelpOptions = true
)
public class CompareCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to the baseline FHIR JSON file or directory."
    )
    private String baselineSource;

    @Parameters(
        index = "1",
        description = "Path to the target FHIR JSON file or directory."
    )
    private String targetSource;

    @Option(
        names = {"-p", "--profile"},
        defaultValue = "US_CORE",
        description = "Target validation profile: US_CORE (default), BASE_R4."
    )
    private String profileName;

    @Option(
        names = {"-f", "--format"},
        defaultValue = "table",
        description = "Output format: table (default ANSI terminal table), json."
    )
    private String format;

    @Option(
        names = {"-o", "--output"},
        description = "Write comparison report to the specified file path instead of stdout."
    )
    private File outputFile;

    @Option(
        names = {"--fail-on-regression"},
        description = "Fail with exit code 1 if any new error-level defect is detected or score drops."
    )
    private boolean failOnRegression;

    @Option(
        names = {"--max-score-drop"},
        description = "Fail with exit code 1 if target score drops by more than N points."
    )
    private Integer maxScoreDrop;

    @Option(
        names = {"-r", "--rules"},
        description = "Path to custom YAML rules file, comma-separated list, or directory."
    )
    private String rulesPath;

    @Option(
        names = {"-v", "--verbose"},
        description = "Display individual new and resolved issue line items in terminal output."
    )
    private boolean verbose;

    private final ComparisonTableRenderer tableRenderer = new ComparisonTableRenderer();
    private final ComparisonJsonRenderer jsonRenderer = new ComparisonJsonRenderer();

    @Override
    public Integer call() {
        if (maxScoreDrop != null && maxScoreDrop < 0) {
            System.err.println("Error: --max-score-drop must be non-negative, was: " + maxScoreDrop);
            return 2;
        }

        String fmt = format != null ? format.toLowerCase() : "";
        if (!fmt.equals("table") && !fmt.equals("json")) {
            System.err.println("Error: Unsupported format '" + format + "'. Supported formats: table, json.");
            return 2;
        }

        ValidationProfile profile;
        try {
            profile = ValidationProfile.fromString(profileName);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }

        File baseFile = new File(baselineSource);
        if (!baseFile.exists()) {
            System.err.println("Error: Baseline file or directory not found: " + baselineSource);
            return 2;
        }

        File targetFile = new File(targetSource);
        if (!targetFile.exists()) {
            System.err.println("Error: Target file or directory not found: " + targetSource);
            return 2;
        }

        List<org.fhirlint.core.rules.custom.FhirPathQualityRule> customRules = java.util.Collections.emptyList();
        if (rulesPath != null && !rulesPath.isBlank()) {
            try {
                customRules = org.fhirlint.core.rules.custom.CustomRuleLoader.loadRules(rulesPath);
            } catch (org.fhirlint.core.rules.custom.CustomRuleException e) {
                System.err.println("Error: " + e.getMessage());
                return 2;
            }
        }

        FhirLinter linter = FhirLinter.create()
                .withProfile(profile)
                .withCustomRules(customRules);

        LintReport baselineReport;
        LintReport targetReport;

        try {
            // 1. Process Baseline sequentially
            if (baseFile.isDirectory()) {
                baselineReport = lintDirectory(baseFile, linter);
            } else {
                baselineReport = linter.lint(baseFile);
            }

            // 2. Process Target sequentially
            if (targetFile.isDirectory()) {
                targetReport = lintDirectory(targetFile, linter);
            } else {
                targetReport = linter.lint(targetFile);
            }
        } catch (FhirParseException e) {
            System.err.println("Error: Boundary verification failed: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("Error: Unexpected failure during linting: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
            e.printStackTrace(System.err);
            return 2;
        }

        // 3. Compare reports
        ComparisonReport report = DatasetComparator.compare(baselineReport, targetReport);
        ComparisonGateResult gateResult = report.evaluateGates(failOnRegression, maxScoreDrop);

        // 4. Render output
        String renderedOutput = fmt.equals("json")
                ? jsonRenderer.render(report, gateResult, failOnRegression, maxScoreDrop)
                : tableRenderer.render(report, verbose, gateResult);

        if (outputFile != null) {
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
                writer.write(renderedOutput);
                System.out.println("Comparison report successfully written to: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error writing output file: " + e.getMessage());
                return 2;
            }
        } else {
            System.out.print(renderedOutput);
        }

        // 5. Evaluate exit code
        if (!gateResult.passed()) {
            System.err.println("Quality regression detected: " + gateResult.failureReason());
            return 1;
        }
        return 0;
    }

    private LintReport lintDirectory(File dir, FhirLinter linter) {
        try (var stream = Files.walk(dir.toPath())) {
            List<File> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .map(Path::toFile)
                    .sorted()
                    .toList();

            if (files.isEmpty()) {
                throw new FhirParseException("No JSON files discovered in directory: " + dir.getAbsolutePath());
            }
            return linter.lint(files);
        } catch (FhirParseException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading directory: " + dir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }
}
