package org.fhirlint.cli.command;

import org.fhirlint.cli.renderer.ConsoleTableRenderer;
import org.fhirlint.cli.renderer.JsonReportRenderer;
import org.fhirlint.cli.renderer.SarifReportRenderer;
import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.model.ValidationProfile;
import org.fhirlint.core.parser.FhirParseException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command to lint FHIR datasets from files, directories, or stdin.
 */
@Command(
    name = "validate",
    description = "Analyze a FHIR dataset and identify data quality, schema, and reference issues.",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to a FHIR JSON file, directory of JSON files, or '-' to read from stdin."
    )
    private String inputSource;

    @Option(
        names = {"-p", "--profile"},
        defaultValue = "US_CORE",
        description = "Target validation profile: US_CORE (default), BASE_R4."
    )
    private String profileName;

    @Option(
        names = {"-f", "--format"},
        defaultValue = "table",
        description = "Output format: table (default ANSI terminal table), json, sarif."
    )
    private String format;

    @Option(
        names = {"--min-score"},
        defaultValue = "0",
        description = "Minimum passing quality score (0-100). Exits with code 1 if score is lower."
    )
    private int minScore;

    @Option(
        names = {"--fail-on"},
        defaultValue = "error",
        description = "Minimum severity that causes exit code 1: error, warning, info."
    )
    private String failOn;

    @Option(
        names = {"-v", "--verbose"},
        description = "Display all issues and detailed diagnostics in terminal output."
    )
    private boolean verbose;

    @Option(
        names = {"-o", "--output"},
        description = "Write output report to the specified file path instead of stdout."
    )
    private File outputFile;

    private final ConsoleTableRenderer tableRenderer = new ConsoleTableRenderer();
    private final JsonReportRenderer jsonRenderer = new JsonReportRenderer();
    private final SarifReportRenderer sarifRenderer = new SarifReportRenderer();

    @Override
    public Integer call() {
        ValidationProfile profile;
        try {
            profile = ValidationProfile.fromString(profileName);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }

        Severity failOnSeverity = parseSeverity(failOn);
        if (failOnSeverity == null) {
            return 2;
        }

        FhirLinter linter = FhirLinter.create().withProfile(profile);
        LintReport report;

        try {
            if ("-".equals(inputSource)) {
                report = linter.lint(System.in);
            } else {
                File file = new File(inputSource);
                if (!file.exists()) {
                    System.err.println("Error: File or directory not found: " + inputSource);
                    return 2;
                }
                if (file.isDirectory()) {
                    report = lintDirectory(file, linter);
                } else {
                    report = linter.lint(file);
                }
            }
        } catch (FhirParseException e) {
            System.err.println("Error: Boundary verification failed: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("Error: Unexpected failure during linting: " + e.getMessage());
            return 2;
        }

        // Render report
        String renderedOutput = switch (format.toLowerCase()) {
            case "json" -> jsonRenderer.render(report);
            case "sarif" -> sarifRenderer.render(report, inputSource);
            default -> tableRenderer.render(report, verbose);
        };

        if (outputFile != null) {
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(renderedOutput);
                System.out.println("Report successfully written to: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error writing output file: " + e.getMessage());
                return 2;
            }
        } else {
            System.out.print(renderedOutput);
        }

        // Evaluate quality gate thresholds
        boolean passed = report.passes(minScore, failOnSeverity);
        return passed ? 0 : 1;
    }

    private LintReport lintDirectory(File dir, FhirLinter linter) {
        try (var stream = Files.walk(dir.toPath())) {
            java.util.List<File> files = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                .map(Path::toFile)
                .sorted()
                .toList();

            if (files.isEmpty()) {
                throw new FhirParseException("Directory contains no .json files: " + dir.getAbsolutePath());
            }
            return linter.lint(files);
        } catch (IOException e) {
            throw new FhirParseException("Failed to scan directory: " + dir.getAbsolutePath(), e);
        }
    }

    private Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return Severity.ERROR;
        }
        try {
            return Severity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Unknown or unsupported severity threshold '" + value + "'. Supported: error, warning, info.");
            return null;
        }
    }
}
