# Quickstart Validation Guide: Phase 7 Standalone Packaging & CI/CD Automation

**Feature**: Phase 7 — Standalone Packaging, Native Image Compilation, and CI/CD Automation
**Date**: 2026-09-28
**Spec Reference**: [spec.md](./spec.md)

---

## Prerequisites
- Java 21+ JDK (for Gradle build and Fat JAR execution)
- GraalVM JDK 21+ with `native-image` (for local native image compilation)
- Docker / Podman (for container execution)

---

## Scenario 1: Building & Running the Universal Fat JAR

### 1. Build the Executable Fat JAR
```bash
./gradlew fatJar
```
*Expected Output*: Generated archive located at `build/libs/fhir-lint-all.jar`.

### 2. Verify Version and Help Flags
```bash
java -jar build/libs/fhir-lint-all.jar --help
java -jar build/libs/fhir-lint-all.jar --version
```
*Expected Output*: Exit code `0`, Picocli usage banner.

### 3. Validate Clean Dataset (Exit Code 0)
```bash
java -jar build/libs/fhir-lint-all.jar validate sample-data/clean/clean-bundle.json
echo "Exit Code: $?"
```
*Expected Output*: Exit Code `0`, 100/100 EXCELLENT score.

### 4. Enforce Quality Gate on Degraded Dataset (Exit Code 1)
```bash
java -jar build/libs/fhir-lint-all.jar validate sample-data/messy/messy-bundle.json --min-score 90
echo "Exit Code: $?"
```
*Expected Output*: Exit Code `1`, quality gate breach logged to `stderr`.

---

## Scenario 2: GraalVM Native Image Compilation & Performance Test

### 1. Generate / Verify Tracing Metadata
```bash
./gradlew test -Pagent
```
*Expected Output*: Metadata recorded in `src/main/resources/META-INF/native-image/org.fhirlint/fhir-lint/`.

### 2. Compile Standalone Native Binary
```bash
./gradlew nativeCompile
```
*Expected Output*: Machine binary created at `build/native/nativeCompile/fhir-lint`.

### 3. Verify Cold Startup Latency (< 50ms)
```bash
time ./build/native/nativeCompile/fhir-lint --version
```
*Expected Output*: Real execution time `< 0.05s` (sub-50ms).

### 4. Verify Piped Stdin Execution
```bash
cat sample-data/clean/clean-bundle.json | ./build/native/nativeCompile/fhir-lint validate - --format json
echo "Exit Code: $?"
```
*Expected Output*: Exit Code `0`, valid JSON emitted.

---

## Scenario 3: Simulating the GitHub Composite Action

### 1. Test Action Inputs Locally using `act` or Shell Test
```bash
# Set simulated action environment
export INPUT_PATH="sample-data/clean/clean-bundle.json"
export INPUT_PROFILE="US_CORE"
export INPUT_FORMAT="table"
export INPUT_FAIL_ON="error"
export GITHUB_OUTPUT="/tmp/action_output.txt"

# Run composite action entry script
./build/native/nativeCompile/fhir-lint validate "$INPUT_PATH" --profile "$INPUT_PROFILE" --format json -o /tmp/report.json
# Extract outputs
SCORE=$(jq -r '.qualityScore.overallScore' /tmp/report.json)
echo "score=$SCORE" >> "$GITHUB_OUTPUT"
cat "$GITHUB_OUTPUT"
```
*Expected Output*: `score=100`, `$GITHUB_OUTPUT` populated.

---

## Scenario 4: OCI Containerized Execution

### 1. Build Distroless Container Image
```bash
docker build -t fhir-lint:local .
```

### 2. Run Volume-Mounted Validation
```bash
docker run --rm \
  -v $(pwd)/sample-data:/workspace \
  fhir-lint:local validate /workspace/clean/clean-bundle.json
```
*Expected Output*: Formatted table output, exit code `0`.

### 3. Run Streamed Piped Stdin Validation
```bash
cat sample-data/messy/messy-bundle.json | docker run -i --rm fhir-lint:local validate - --format sarif
```
*Expected Output*: OASIS SARIF 2.1.0 JSON emitted directly to stdout.

---

## Scenario 5: Checksum Generation and Verification

### 1. Generate SHA-256 Checksum
```bash
cd build/libs && sha256sum fhir-lint-all.jar > fhir-lint-all.jar.sha256
```

### 2. Verify Checksum
```bash
cd build/libs && sha256sum -c fhir-lint-all.jar.sha256
```
*Expected Output*: `fhir-lint-all.jar: OK`.
