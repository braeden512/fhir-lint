# ADR-007: Phase 7 — Standalone Packaging, Native Image Compilation, and CI/CD Automation

## Status
Accepted (Amended to reflect ADR-009)

## Context & Problem Statement
To make FHIRLint a production-grade developer tool, distribution must be frictionless across different operating environments (macOS, Linux, Windows, CI/CD runners) without requiring users to configure complex runtime dependencies or install a dedicated JVM.

Under the local-first architecture (ADR-009), persistent relational databases and background server daemons are completely eliminated. Production engineering focuses on:
1. Standalone packaging and distribution formats.
2. Ahead-of-time (AOT) compilation for instant startup and zero JVM installation.
3. Automated CI/CD integration gates.

## Decision Drivers
1. **Zero-Friction Installation**: Users can download a single executable file and immediately run `fhir-lint validate bundle.json`.
2. **Sub-Second Execution**: Instant startup (~15ms) for interactive terminal use and fast CI/CD pipeline steps.
3. **Multi-Platform Support**: Linux, macOS, and Windows compatibility.
4. **Automated Quality Gates**: Drop-in GitHub Action for automated pull request quality analysis.

## Considered Options
1. **Fat JAR (Shadow / Spring Boot jar)**:
   - Requires Java 21+ JRE pre-installed on the host. Easy to build, but creates adoption friction for non-Java teams.
2. **GraalVM Native Image**:
   - Ahead-of-time compiles Java bytecode into a standalone machine binary.
   - Zero JVM requirement on user machine; instant startup.
   - Higher build complexity and memory during compilation.
3. **Docker Containerized Runner**:
   - `docker run --rm -v $(pwd):/data fhir-lint validate /data/bundle.json`.
   - Highly portable across environments with Docker.

## Decision Outcome
Adopt **Multi-Tier Distribution**:
1. **Primary CLI Executable**: GraalVM Native Image binary (`fhir-lint`) distributed via GitHub Releases.
2. **Universal Fat JAR**: Runnable JAR for JVM environments (`java -jar fhir-lint.jar`).
3. **GitHub Action**: Official GitHub Action (`uses: fhir-lint/action@v1`) for repository quality gating.
4. **Lightweight Container**: Minimal distroless container image for containerized CI/CD systems (GitLab CI, Tekton, Argo).

## Consequences
### Positive
- Completely removes database operational overhead (zero Flyway, zero PostgreSQL, zero connection pooling).
- Test execution time drops from minutes (waiting on Testcontainers) to sub-second JUnit 5 test runs.
- Non-Java developers run the native binary with zero Java runtime installation.

### Negative / Trade-offs
- GraalVM Native Image compilation requires configuring reflection hints for HAPI FHIR model classes and Jackson serializers.
