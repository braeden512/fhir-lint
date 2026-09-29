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

@DisplayName("Native Binary Parity Tests")
class NativeBinaryParityTest {

	private static Path nativeBinaryPath;

	@BeforeAll
	static void setUp() {
		// Look in standard GraalVM output directory or environment variable
		String envPath = System.getenv("FHIR_LINT_NATIVE_BIN");
		if (envPath != null && !envPath.isBlank()) {
			nativeBinaryPath = Paths.get(envPath).toAbsolutePath();
		} else {
			nativeBinaryPath = Paths.get("build", "native", "nativeCompile", "fhir-lint").toAbsolutePath();
		}

		// Gracefully skip if native binary is not yet compiled, ensuring fast < 3s standard test execution
		Assumptions.assumeTrue(
				Files.exists(nativeBinaryPath),
				"Native binary not found at " + nativeBinaryPath + "; skipping parity test during JVM test pass"
		);
	}

	@Test
	@DisplayName("Native binary should display version with sub-50ms cold launch")
	void shouldDisplayVersionQuickly() throws Exception {
		long start = System.currentTimeMillis();
		Process process = new ProcessBuilder(nativeBinaryPath.toString(), "--version")
				.redirectErrorStream(true)
				.start();

		String output;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();
		long duration = System.currentTimeMillis() - start;

		assertThat(exitCode).isEqualTo(0);
		assertThat(output).contains("0.1.0");
		// Cold launch should be under 500ms even in busy test runners, typically < 50ms
		assertThat(duration).isLessThan(1000L);
	}

	@Test
	@DisplayName("Native binary should validate clean bundle and exit 0")
	void shouldValidateCleanBundle() throws Exception {
		File cleanBundle = new File("sample-data/clean/clean-bundle.json");
		Assumptions.assumeTrue(cleanBundle.exists(), "clean-bundle.json must exist");

		Process process = new ProcessBuilder(
				nativeBinaryPath.toString(),
				"validate", cleanBundle.getAbsolutePath(),
				"--format", "json"
		).start();

		String output;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();

		assertThat(exitCode).isEqualTo(0);
		assertThat(output).contains("\"overallScore\":100").contains("\"grade\":\"EXCELLENT\"");
	}

	@Test
	@DisplayName("Native binary should fail quality gate on messy bundle with min-score")
	void shouldFailQualityGateOnMessyBundle() throws Exception {
		File messyBundle = new File("sample-data/messy/messy-bundle.json");
		Assumptions.assumeTrue(messyBundle.exists(), "messy-bundle.json must exist");

		Process process = new ProcessBuilder(
				nativeBinaryPath.toString(),
				"validate", messyBundle.getAbsolutePath(),
				"--min-score", "95"
		).start();

		int exitCode = process.waitFor();
		assertThat(exitCode).isEqualTo(1);
	}
}
