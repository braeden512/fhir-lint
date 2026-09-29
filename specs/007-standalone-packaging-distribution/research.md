# Technical Research & Architecture Decisions: Phase 7 Standalone Packaging & CI/CD Automation

**Feature**: Phase 7 — Standalone Packaging, Native Image Compilation, and CI/CD Automation
**Date**: 2026-09-28
**Spec Reference**: [spec.md](./spec.md)

---

## 1. Fat JAR Packaging Strategy

### Decision
Use a dedicated Gradle `fatJar` task utilizing standard Gradle archive packaging (`DuplicatesStrategy.EXCLUDE` with `from { configurations.runtimeClasspath.collect { ... } }`) or the modern `com.gradleup.shadow` plugin (v8.3.6+ compatible with Gradle 9+), producing `build/libs/fhir-lint-all.jar`.

### Rationale
- Standard Gradle configuration requires zero external repository dependencies and has zero plugin incompatibility risks across major Gradle upgrades (Gradle 9+).
- Bundles all runtime dependencies (`hapi-fhir-base`, `hapi-fhir-structures-r4`, `picocli`, `jackson`, `logback`, `slf4j`).
- Sets `Main-Class: org.fhirlint.cli.FhirLintApplication` in `META-INF/MANIFEST.MF`.
- Runnable instantly on any host with Java 21+ via `java -jar fhir-lint-all.jar validate ...`.

### Alternatives Considered
- *Spring Boot Repackage*: Rejected because Spring Boot was explicitly removed from the architecture (Constitution Principle II, ADR-009).
- *Gradle Application `installDist`*: Requires a zip/tar containing a directory with shell wrapper scripts and a `lib/` folder with dozens of JAR files. Fails the single-file distribution requirement.

---

## 2. GraalVM Native Image Tooling & Configuration

### Decision
Adopt the official GraalVM Native Build Tools Gradle plugin (`org.graalvm.buildtools.native` v0.10.4) to compile `fhir-lint` into an ahead-of-time (AOT) machine binary.

### Configuration
```groovy
plugins {
    id 'org.graalvm.buildtools.native' version '0.10.4'
}

graalvmNative {
    binaries {
        main {
            imageName = 'fhir-lint'
            mainClass = 'org.fhirlint.cli.FhirLintApplication'
            buildArgs.addAll(
                '--no-fallback',
                '-H:+ReportExceptionStackTraces',
                '--enable-url-protocols=http,https',
                '-H:IncludeResources=.*\\.json$',
                '-H:IncludeResources=profiles/.*'
            )
        }
    }
}
```

### Rationale
- `--no-fallback`: Guarantees a true standalone native binary without falling back to a JVM launcher.
- Enables instant startup (< 20ms) and minimal resident memory (< 60MB).
- First-class Gradle integration via `./gradlew nativeCompile` and `./gradlew nativeTest`.

### Alternatives Considered
- *Subprocess Jlink / Jpackage runtime*: Creates a 40–80MB bundled JVM folder rather than a single binary executable; slower startup (> 250ms).
- *Quarkus / Micronaut Native*: Introduces a full enterprise framework for a CLI tool, violating Constitution Principle II (Lean, Dependency-Minimized Architecture).

---

## 3. AOT Reachability Metadata for HAPI FHIR & Jackson

### Decision
Utilize the GraalVM Native Image Tracing Agent (`-Pagent` via the Gradle Native plugin) running against the complete integration test suite (`clean-bundle.json`, `messy-bundle.json`, synthetic datasets) to automatically record reflection, serialization, JNI, and resource access metadata into `src/main/resources/META-INF/native-image/org.fhirlint/fhir-lint/`.

### Rationale
- HAPI FHIR R4 dynamically loads resource structures (`Patient`, `Observation`, `Bundle`, etc.) and profile packages (`package.tgz`) via reflection and classpath scanning (`FhirContext.forR4()`).
- Jackson uses reflection to serialize `LintReport`, `QualityScore`, and `QualityIssue`.
- Picocli uses reflection to map CLI options and commands.
- The tracing agent intercepts all dynamic lookups during real linting passes and writes exact `reflect-config.json`, `resource-config.json`, and `serialization-config.json` files, preventing runtime `ClassNotFoundException` in AOT binaries.
- The generated metadata files are committed to source control and validated continuously in CI via `./gradlew nativeTest`.

### Alternatives Considered
- *Manual JSON reflection config*: Fragile and prone to missing internal HAPI FHIR classes whenever dependencies update.
- *Static GraalVM feature extensions*: Higher complexity and maintenance burden without additional performance benefit.

---

## 4. GitHub Action Implementation Architecture

### Decision
Implement the official GitHub Action as a **Composite Action** located at repository root (`action.yml`), which detects the runner operating system and executes the native binary or universal JAR directly on the host runner.

### Rationale
- **Cross-Platform Compatibility**: Composite actions execute natively across Linux (`ubuntu-latest`), macOS (`macos-latest`), and Windows (`windows-latest`). Docker-based GitHub actions only run on Linux runners and fail on macOS/Windows.
- **Speed**: Executing the native binary directly on the runner eliminates container pull latency, running in milliseconds.
- **Flexibility**: Can fall back to `java -jar` if running on an architecture where pre-built binaries are not downloaded.

### Alternatives Considered
- *Docker Container Action*: GitHub Actions only supports Docker actions on Linux runners; fails SC-004 on macOS and Windows runners.
- *JavaScript / Node Action (`@actions/core`)*: Requires maintaining a separate Node.js / TypeScript build, npm dependencies, and dist bundling; unnecessary complexity when a clean composite action can orchestrate the executable directly.

---

## 5. Deterministic Step Output Extraction

### Decision
The GitHub Action invokes the linter with `--output <temp-report.json> --format json` internally, while echoing the requested user format (`--format table` by default) to standard output. A lightweight shell step parses the JSON report and writes structured variables directly to `$GITHUB_OUTPUT`:

```bash
echo "score=$SCORE" >> "$GITHUB_OUTPUT"
echo "grade=$GRADE" >> "$GITHUB_OUTPUT"
echo "errors=$ERRORS" >> "$GITHUB_OUTPUT"
echo "warnings=$WARNINGS" >> "$GITHUB_OUTPUT"
echo "passed=$PASSED" >> "$GITHUB_OUTPUT"
```

### Rationale
- Scraping ANSI table output with regex is fragile and breaks whenever formatting or terminal styling changes.
- Writing to `$GITHUB_OUTPUT` *before* checking the quality gate exit code ensures downstream workflow steps always receive output variables, even when the quality gate fails (exit code 1).

### Alternatives Considered
- *Regex parsing stdout table*: Extremely fragile to ANSI escape codes and table border changes.
- *Requiring users to set format to JSON*: Degrades human readability in GitHub Actions run logs.

---

## 6. Container Image Base & Security Model

### Decision
Build a minimal, multi-stage OCI container image based on `gcr.io/distroless/cc-debian12:nonroot` packaging the Linux x86_64 GraalVM native binary.

### Specification
```dockerfile
# Stage 1: Build binary using GraalVM or package pre-built
FROM ghcr.io/graalvm/native-image-community:21 AS builder
...
# Stage 2: Distroless runtime with glibc
FROM gcr.io/distroless/cc-debian12:nonroot
COPY --from=builder /build/fhir-lint /fhir-lint
USER 65532:65532
ENTRYPOINT ["/fhir-lint"]
CMD ["--help"]
```

### Rationale
- **Zero Attack Surface**: Distroless contains no package managers, shells, or unnecessary utilities.
- **Small Footprint**: Image size under 60MB.
- **Sub-Second Cold Start**: Native binary starts in ~15ms inside container.
- **Enterprise Security Compliance**: Runs as non-root UID 65532, passing container security audits.

### Alternatives Considered
- *Alpine Linux + JRE*: Image size is 250MB+ with 1–2 second JVM bootstrap latency.
- *Ubuntu / Debian base*: Unnecessary bloat and high CVE exposure.

---

## 7. Multi-Platform CI Release Matrix & SHA-256 Checksums

### Decision
Implement `.github/workflows/release.yml` triggered on release tags (`v*`). The workflow runs a GitHub Actions job matrix across 4 native OS runners:
1. `ubuntu-latest` $\rightarrow$ `fhir-lint-linux-x86_64`
2. `macos-14` (Apple Silicon M-series) $\rightarrow$ `fhir-lint-macos-aarch64`
3. `macos-13` (Intel) $\rightarrow$ `fhir-lint-macos-x86_64`
4. `windows-latest` $\rightarrow$ `fhir-lint-windows-x86_64.exe`
5. Universal Job $\rightarrow$ `fhir-lint-all.jar`

Each job computes `sha256sum <binary> > <binary>.sha256`, and an aggregation step uploads all binaries and checksums to the published GitHub Release.

### Rationale
- GraalVM Native Image does not support cross-compilation; binaries must be compiled on native OS hardware.
- Cryptographic checksums verify file authenticity and supply chain security for end-users.
- Standardized file naming matches common package managers (Homebrew, Scoop).

---

## 8. Summary of Technical Choices

| Area | Decision | Reason |
| :--- | :--- | :--- |
| **Fat JAR** | Gradle `fatJar` task | Pure Gradle, single executable JAR, runs anywhere Java 21 exists |
| **Native Compiler** | GraalVM Native Build Tools 0.10.4 | Sub-50ms startup, single OS binary, zero JVM prerequisite |
| **Metadata Tracing** | GraalVM Native Tracing Agent (`-Pagent`) | Automated reflection config for HAPI FHIR & Jackson |
| **GitHub Action** | GitHub Composite Action (`action.yml`) | Cross-platform (Ubuntu, macOS, Windows) without container limits |
| **Output Extraction** | Internal JSON report extraction | Deterministic `$GITHUB_OUTPUT` without scraping ANSI tables |
| **Container Base** | Distroless static non-root | < 60MB image, zero CVE attack surface, runs as non-root |
| **CI Releases** | 4-OS runner matrix + SHA-256 checksums | Required for AOT compilation without cross-compilation |
