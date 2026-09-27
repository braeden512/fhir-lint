<!--
SYNC IMPACT REPORT
- Version Change: 1.0.0 -> 2.0.0
- Rationale: Architectural pivot from hosted Spring Boot REST service to a zero-infrastructure standalone CLI and embeddable Java core engine.
- Modified Principles:
  * Principle II (Maintainable Spring Boot Architecture) -> II. Lean, Dependency-Minimized Architecture (Pure Java 21, framework-agnostic core engine, zero database).
  * Principle VII (Developer-Focused API Design) -> VII. Developer-Focused CLI & Library Design (POSIX CLI standards, ANSI tables, JSON/SARIF output, standard exit codes).
- Modified Sections:
  * Technology Stack & Architectural Constraints: Removed Spring Boot, WebMVC, JPA, and PostgreSQL. Added Picocli. Mandated zero-database stateless in-memory execution.
  * Development Workflow & Quality Gates: Removed OpenAPI schema checks; added CLI exit code and output format test requirements.
- Removed Sections: None
- Follow-up TODOs: Update PRODUCT_SPEC.md, ADRs, and spec-001 to align with Constitution 2.0.0.
-->

# FHIRLint Constitution

## Core Principles

### I. Standards-First Healthcare Interoperability
FHIRLint MUST utilize established FHIR R4 standards and the HAPI FHIR library rather than implementing custom parsers, serializers, or data models unnecessarily. The application MUST NOT invent behavior, extensions, or validation rules that conflict with established HL7 FHIR R4 specifications or semantics.
*Rationale*: Reimplementing complex medical interoperability standards is error-prone, violates compliance, and defeats the goal of native healthcare system integration.

### II. Lean, Dependency-Minimized Architecture
The core linting engine MUST be pure, framework-agnostic Java 21 with zero framework lock-in. Unnecessary abstractions, heavy frameworks, persistent databases, and web servers MUST NOT be introduced. The core engine and CLI MUST be completely stateless and run in-process or locally without requiring background daemons, relational databases, or external network connectivity.
*Rationale*: Linters must be fast, lightweight, and easily integrated into local development and CI/CD pipelines. Avoiding heavy web and database frameworks eliminates operational costs and removes infrastructure complexity.

### III. Testable and Reliable Software
All critical quality rules, custom linting engines, and CLI interfaces MUST have comprehensive, automated test coverage. Tests SHOULD verify meaningful, outer-loop behavior (such as end-to-end CLI execution, exit code contracts, and validator outputs) rather than fragile, mock-heavy implementation details. The application MUST NOT claim or document any functionality that is not backed by an active, passing automated test.
*Rationale*: High-quality healthcare software requires rock-solid reliability. Ensuring documented features are fully tested prevents regression and false promises of data quality.

### IV. Actionable Data-Quality Analysis
Validation and analysis findings MUST explain clearly what is wrong, where in the resource it occurs (using standard FHIRPath/JSONPath), why the issue matters, and provide a concrete action a developer can take to resolve it. FHIRLint MUST clearly distinguish low-level structural FHIR validation (e.g., schema conformance) from higher-level data-quality, clinical-context, or completeness analysis. Quality scores produced by the system MUST be documented as engineering/integrator indicators rather than clinical, medical, or regulatory compliance measurements.
*Rationale*: Vague linting errors frustrate developers. Providing actionable feedback and keeping quality scores scoped to engineering avoids clinical misunderstandings.

### V. Privacy-Conscious Healthcare Software
All development, demo, and automated test environments MUST utilize exclusively synthetic, de-identified, or non-PHI (Protected Health Information) data. FHIRLint MUST operate with a strict zero-retention posture: data analyzed by the tool is held transiently in memory for the duration of the linting pass and MUST NOT be written to persistent databases, log sinks, or remote telemetry. The system and its documentation MUST explicitly state that FHIRLint is not clinical decision support, does not guarantee clinical correctness, and is not a substitute for clinical validation.
*Rationale*: Healthcare software must treat privacy as a core engineering pillar. A local-first, zero-persistence model guarantees that clinical data never escapes the user's security perimeter.

### VI. Incremental Development and Simplicity
Developers MUST build and deliver the Minimum Viable Product (MVP) before adding infrastructure or features that are not explicitly justified by current, verified user requirements. Simpler, monolithic and library-first solutions MUST be preferred over premature distributed systems, microservices, or early optimizations. Every new external dependency MUST require written justification and approval.
*Rationale*: Premature complexity is the root of most software failure. Keeping things simple preserves speed and keeps the system maintainable.

### VII. Developer-Focused CLI & Library Design
FHIRLint is a developer tool; its primary interfaces are its command-line interface (CLI) and an embeddable Java library API. The CLI MUST adhere to standard POSIX conventions: accept files, directories, or standard input; return standard exit codes (0 for success, non-zero for quality gate failures or syntax errors); and provide human-friendly colorized ANSI terminal formatting as well as machine-readable JSON and SARIF (Static Analysis Results Interchange Format) outputs for seamless CI/CD integration.
*Rationale*: Developers expect linters to integrate frictionlessly into terminal workflows, pre-commit hooks, and CI/CD pipelines (like GitHub Actions).

## Technology Stack & Architectural Constraints
To guarantee consistency and technical safety, FHIRLint MUST adhere to the following stack boundaries:
- **Core Platform**: Java 21+.
- **Healthcare Libraries**: HAPI FHIR for parsing, model representation, and baseline conformance validation. Custom code MUST NOT recreate standard FHIR resource parsers or serializers.
- **CLI Framework**: Picocli for command-line parsing, ANSI styling, and native compilation compatibility.
- **Data Stores**: None. The system is strictly stateless and operates entirely in memory. No databases, persistent disk caches, or external network connections are permitted for core linting operations.
- **Dependency Control**: External libraries MUST have clear justification. Any additions to `build.gradle` require architectural justification and review.

## Development Workflow & Quality Gates
Development of FHIRLint is structured around quality and simplicity:
- **No Untested Claims**: No functional capabilities can be documented, announced, or committed without a corresponding automated integration/unit test in the codebase.
- **CLI & Contract Consistency**: CLI argument parsing, exit codes, and output serialization formats (ANSI table, JSON, SARIF) MUST be verified with automated test suites.
- **Code Review & Standards**: All pull requests must verify compliance with this Constitution and must run the project check task (`./gradlew check`) to ensure no compile errors, linter violations, or failing tests exist.

## Governance
1. **Supremacy**: This Constitution represents the highest engineering and governance authority for the FHIRLint project. All design plans, architecture decision records (ADRs), pull requests, and implementations must comply with the principles outlined herein.
2. **Amendment Procedure**: Amendments to this Constitution require a formal proposal, thorough discussion of alternatives, and a consensus decision by the project maintainers. Any amendment must be recorded by incrementing the version number (using semantic versioning rules) and updating the Last Amended Date.
3. **Compliance Review**: All future specifications, implementation plans, and Pull Requests (PRs) must explicitly reference compliance with these principles. If a design violates or seeks to bypass any of these rules, it must be rejected or the Constitution itself must be amended first.
4. **Tooling & Guidance**: Runtime development decisions, style guidelines, and code linting settings must be kept in sync with these principles. Use `../../PRODUCT_SPEC.md` for specification details.

**Version**: 2.0.0 | **Ratified**: 2026-09-25 | **Last Amended**: 2026-09-26
