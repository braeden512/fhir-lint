package org.fhirlint.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fat JAR Execution Tests")
class FatJarExecutionTest {

	private static Path fatJarPath;
	private static String javaBin;

	@BeforeAll
	static void setUp() {
		javaBin = ProcessHandle.current().info().command().orElse("java");
		fatJarPath = Paths.get("build", "libs", "fhir-lint-all.jar").toAbsolutePath();
		Assumptions.assumeTrue(Files.exists(fatJarPath), "fhir-lint-all.jar must exist before running FatJarExecutionTest");
	}

	@Test
	@DisplayName("Should display help message and exit 0")
	void shouldDisplayHelpAndExitZero() throws Exception {
		Process process = new ProcessBuilder(javaBin, "-jar", fatJarPath.toString(), "--help")
				.redirectErrorStream(true)
				.start();

		String output;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();

		assertThat(exitCode).isEqualTo(0);
		assertThat(output).contains("Usage: fhir-lint").contains("validate");
	}

	@Test
	@DisplayName("Should display version and exit 0")
	void shouldDisplayVersionAndExitZero() throws Exception {
		Process process = new ProcessBuilder(javaBin, "-jar", fatJarPath.toString(), "--version")
				.redirectErrorStream(true)
				.start();

		String output;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();

		assertThat(exitCode).isEqualTo(0);
		assertThat(output).contains("0.1.0");
	}

	@Test
	@DisplayName("Should validate clean dataset and exit 0")
	void shouldValidateCleanDatasetAndExitZero() throws Exception {
		File cleanBundle = new File("sample-data/clean/clean-bundle.json");
		Assumptions.assumeTrue(cleanBundle.exists(), "clean-bundle.json must exist");

		Process process = new ProcessBuilder(
				javaBin, "-jar", fatJarPath.toString(),
				"validate", cleanBundle.getAbsolutePath(),
				"--format", "json"
		).start();

		String output;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();

		assertThat(exitCode).isEqualTo(0);
		assertThat(output).contains("\"overallScore\"").contains("\"grade\"");
	}

	@Test
	@DisplayName("Should fail quality gate and exit 1 on degraded dataset with strict threshold")
	void shouldFailQualityGateAndExitOne() throws Exception {
		File messyBundle = new File("sample-data/messy/messy-bundle.json");
		Assumptions.assumeTrue(messyBundle.exists(), "messy-bundle.json must exist");

		Process process = new ProcessBuilder(
				javaBin, "-jar", fatJarPath.toString(),
				"validate", messyBundle.getAbsolutePath(),
				"--min-score", "95"
		).start();

		String errorOutput;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			errorOutput = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();

		assertThat(exitCode).isEqualTo(1);
		assertThat(errorOutput).contains("Quality gate breach");
	}
}
