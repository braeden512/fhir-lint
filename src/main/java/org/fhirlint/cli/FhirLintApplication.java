package org.fhirlint.cli;

import org.fhirlint.cli.command.ValidateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main command-line application entry point for FHIRLint.
 */
@Command(
    name = "fhir-lint",
    description = "FHIRLint: The Developer-Focused Data Quality Linter for FHIR Healthcare Data.",
    subcommands = {
        ValidateCommand.class
    },
    mixinStandardHelpOptions = true,
    version = "0.1.0"
)
public class FhirLintApplication implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FhirLintApplication())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // When invoked without subcommands, display usage help
        CommandLine.usage(this, System.out);
    }
}
