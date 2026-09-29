# Implementation Plan: Phase 7 — Standalone Packaging, Native Image Compilation, and CI/CD Automation

**Branch**: `007-standalone-packaging-distribution` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-standalone-packaging-distribution/spec.md`

---

## Summary

Phase 7 establishes production-grade packaging, distribution, and CI/CD automation for FHIRLint. Guided by ADR-007, ADR-009, and Constitution v2.0.0, this phase completely eliminates legacy databases and background server daemons in favor of a multi-tier standalone distribution architecture:
1. **Universal Fat JAR**: An all-inclusive executable archive (`fhir-lint-all.jar`) runnable on any Java 21+ environment.
2. **GraalVM Ahead-Of-Time (AOT) Native Image**: A standalone OS binary (`fhir-lint`) with sub-50ms cold launch and zero JVM prerequisites, supported by reachability metadata generated via the GraalVM Native Image Tracing Agent.
3. **Official GitHub Composite Action**: Cross-platform quality gating action (`action.yml`) running natively on Ubuntu, macOS, and Windows with deterministic `$GITHUB_OUTPUT` extraction and SARIF upload integration.
4. **Lightweight OCI Container**: Secure, non-root distroless container (`gcr.io/distroless/cc-debian12:nonroot`) packaging the Linux native binary for container-native CI platforms (GitLab CI, Tekton, Argo).
5. **Multi-Platform Release Pipeline**: Matrix-driven GitHub Actions release workflow publishing standardized platform binaries and SHA-256 checksums.

---

## Technical Context

**Language/Version**: Java 21+ (OpenJDK 21 / GraalVM JDK 21)

**Primary Dependencies**:
- Gradle 9.7.1
- HAPI FHIR R4 (6.10.0)
- Picocli (4.7.6)
- Jackson (2.18.2)
- GraalVM Native Build Tools Gradle Plugin (`org.graalvm.buildtools.native:0.10.4`)

**Storage**: None. Strictly stateless and in-memory execution in compliance with Constitution Principle II and V.

**Testing**: JUnit 5, AssertJ, Gradle `nativeTest` for AOT runtime verification.

**Target Platform**: Linux (x86_64), macOS (Apple Silicon / Intel), Windows (x86_64), Universal JVM.

**Project Type**: Standalone CLI executable, Fat JAR, GitHub Composite Action, and OCI Container.

**Performance Goals**:
- Native binary launch: < 50ms for `--version` and `--help`.
- In-memory validation of < 1,000 resources: < 500ms end-to-end.
- Container image size: < 60MB.

**Constraints**:
- Zero database overhead (no Flyway, no PostgreSQL, no daemon services).
- Zero retention / zero remote telemetry for healthcare data privacy (Constitution Principle V).
- POSIX-compliant exit codes (`0`, `1`, `2`) across all distribution formats.

**Scale/Scope**: 5 distribution targets, 1 GitHub Composite Action, 1 Dockerfile, 1 release workflow.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Requirement | Plan Alignment | Status |
| :--- | :--- | :--- | :---: |
| **I. Standards-First Healthcare Interoperability** | Built on HAPI FHIR R4; no custom standard reinvention. | Retains official HAPI FHIR R4 structures and US Core validation resources. | **PASS** |
| **II. Lean, Dependency-Minimized Architecture** | Pure Java 21 core, zero framework lock-in, zero database, zero background daemons. | Standalone Fat JAR and Native Image; all DB/server dependencies eliminated. | **PASS** |
| **III. Testable and Reliable Software** | Documented features backed by active tests; automated CLI contract tests. | Unit tests, CLI tests, native binary test task (`nativeTest`), and release sanity validation. | **PASS** |
| **IV. Actionable Data-Quality Analysis** | Actionable issues, standard locations, clear non-clinical indicator disclaimer. | All renderers (Table, JSON, SARIF) include mandatory non-clinical disclaimer. | **PASS** |
| **V. Privacy-Conscious Healthcare Software** | Zero-retention posture; no data written to persistent databases or telemetry. | Completely local-first in-memory processing across all packaging formats. | **PASS** |
| **VI. Incremental Development & Simplicity** | Monolithic/library-first before distributed systems; minimal dependencies. | Clean single-binary distribution and lightweight composite action. | **PASS** |
| **VII. Developer-Focused CLI Design** | POSIX conventions, standard exit codes (`0`, `1`, `2`), ANSI tables, JSON, SARIF. | Identical argument parsing and exit codes across Fat JAR, binary, container, and action. | **PASS** |

**Constitution Gate Verdict**: **ALL GATES PASSED**

---

## Project Structure

### Documentation (this feature)

```text
specs/007-standalone-packaging-distribution/
├── plan.md              # This implementation plan
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Entities, action configuration, and metadata models
├── quickstart.md        # Runnable end-to-end validation guide
├── contracts/           # Interface contracts
│   ├── action-contract.md    # GitHub Composite Action contract
│   ├── container-contract.md # OCI Container contract
│   └── release-contract.md   # Multi-platform release manifest & checksums
└── checklists/
    └── requirements.md  # Quality validation checklist
```

### Source Code & Configuration Layout

```text
fhir-lint/
├── .github/
│   └── workflows/
│       ├── build.yml                 # Continuous integration workflow
│       └── release.yml               # Multi-platform native release matrix & publishing
├── action.yml                        # Official GitHub Composite Action
├── Dockerfile                        # Multi-stage distroless container image
├── build.gradle                      # Build script with fatJar and GraalVM native plugin
├── src/
│   └── main/
│       ├── java/org/fhirlint/
│       │   ├── cli/                  # Picocli command-line app & renderers
│       │   └── core/                 # Pure Java linting engine
│       └── resources/
│           └── META-INF/
│               └── native-image/org.fhirlint/fhir-lint/
│                   ├── reflect-config.json         # AOT reflection metadata
│                   ├── resource-config.json        # AOT bundled resource metadata
│                   ├── serialization-config.json   # AOT serialization metadata
│                   └── native-image.properties     # GraalVM compiler flags
└── sample-data/
    ├── clean/clean-bundle.json
    └── messy/messy-bundle.json
```

**Structure Decision**: Standard root-level distribution packaging conforming to GitHub Action and Docker ecosystem conventions (`action.yml` and `Dockerfile` at repository root, native image metadata under `src/main/resources/META-INF/native-image`).

---

## Complexity Tracking

> **Constitution Check has zero violations. No unnecessary complexity introduced.**

| Component | Justification | Simpler Alternative Evaluated |
| :--- | :--- | :--- |
| **GraalVM Native Image** | Eliminates 200MB+ JVM prerequisite for non-Java developers; sub-50ms startup | Fat JAR only (retained as universal tier, but native binary is required for non-Java adoption) |
| **GitHub Composite Action** | Runs cross-platform on Ubuntu, macOS, and Windows runners natively | Docker Action (rejected because GitHub only supports Docker on Linux runners) |
| **Distroless Container** | Minimal attack surface, zero CVEs, non-root user | Alpine/Debian with full shell (unnecessary attack surface) |
