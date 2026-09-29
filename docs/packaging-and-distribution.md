# Packaging, Distribution, and CI/CD Automation Guide

This guide documents the multi-tier distribution architecture for FHIRLint implemented in Phase 7, conforming to [ADR-007](adr/ADR-007-phase-7-production-engineering-and-persistence.md) and [ADR-009](adr/ADR-009-pivot-to-standalone-cli-and-core-engine.md).

---

## 1. Universal Executable Fat JAR

The universal Fat JAR packages all compiled code and transitive runtime dependencies (HAPI FHIR R4, Picocli, Jackson, SLF4J, Logback) into a single archive with signature exclusions.

### Building
```bash
./gradlew fatJar
```
Output location: `build/libs/fhir-lint-all.jar` (~123MB).

### Running
```bash
java -jar build/libs/fhir-lint-all.jar validate sample-data/clean/clean-bundle.json
```

---

## 2. GraalVM Ahead-Of-Time (AOT) Native Image

FHIRLint compiles into a standalone, single-file machine binary with near-instant startup (< 50ms) and zero JVM prerequisite.

### Reachability Metadata
Dynamic reflection and resource lookups (for HAPI FHIR R4 models, Jackson serializers, and Picocli) are defined under:
`src/main/resources/META-INF/native-image/org.fhirlint/fhir-lint/`
- `reflect-config.json`: Registered types for reflection
- `resource-config.json`: Bundled resources (including US Core package tarball and Logback config)
- `serialization-config.json`: Serialization types
- `native-image.properties`: Compiler flags (`--no-fallback`, stack trace reporting, URL protocols)

### Compiling
```bash
./gradlew nativeCompile
```
Output location: `build/native/nativeCompile/fhir-lint`

---

## 3. Official GitHub Composite Action (`action.yml`)

The official GitHub Composite Action enables automated healthcare data quality gating across all GitHub-hosted runners (`ubuntu-latest`, `macos-latest`, `windows-latest`).

### Example Workflow
```yaml
- name: FHIRLint Quality Gate
  uses: fhir-lint/action@v1
  with:
    path: 'sample-data/clean/clean-bundle.json'
    profile: 'US_CORE'
    min-score: 85
    fail-on: 'error'
    upload-sarif: 'true'
```

### Outputs Exported to `$GITHUB_OUTPUT`
- `score`: Overall quality score (0–100)
- `grade`: Engineering grade tier (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`)
- `errors`: Error count
- `warnings`: Warning count
- `passed`: `true` or `false`
- `report-path`: File path to saved report

---

## 4. OCI Container Image

The container image packages the Linux x86_64 native binary on a minimal, secure, non-root distroless base image (`gcr.io/distroless/cc-debian12:nonroot`) with `glibc` dynamic linking support.

### Building
```bash
docker build -t fhir-lint:latest .
```

### Running with Mounted Volumes
```bash
docker run --rm \
  -v $(pwd)/sample-data:/workspace/sample-data \
  fhir-lint:latest validate /workspace/sample-data/clean/clean-bundle.json
```

---

## 5. Multi-Platform Release Pipeline

Release tagging (`v*.*.*`) triggers `.github/workflows/release.yml`, executing an OS runner matrix:
- `ubuntu-latest` $\rightarrow$ `fhir-lint-linux-x86_64`
- `macos-14` $\rightarrow$ `fhir-lint-macos-aarch64`
- `macos-13` $\rightarrow$ `fhir-lint-macos-x86_64`
- `windows-latest` $\rightarrow$ `fhir-lint-windows-x86_64.exe`
- Universal $\rightarrow$ `fhir-lint-all.jar`

Every asset is accompanied by a cryptographic `.sha256` checksum for supply chain verification.

---

## 6. Homebrew Package Manager (macOS & Linux)

FHIRLint maintains an official Homebrew formula at [`Formula/fhir-lint.rb`](../Formula/fhir-lint.rb) allowing single-command installation across macOS and Linux.

### Installation via Tap
```bash
brew tap braeden512/fhir-lint
brew install fhir-lint
```

### Direct Installation
```bash
brew install braeden512/fhir-lint/fhir-lint
```

For detailed architectural details, checksum automation, and instructions on submitting to Homebrew Core (`brew install fhir-lint`), see the complete [Homebrew Distribution Guide](homebrew-distribution.md).

