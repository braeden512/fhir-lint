<!--
SYNC IMPACT REPORT
- Version Change: None/Template -> 1.0.0
- Rationale: Initial constitution creation for FHIRLint.
- Modified Principles:
  * [PRINCIPLE_1_NAME] -> I. Standards-First Healthcare Interoperability
  * [PRINCIPLE_2_NAME] -> II. Maintainable Spring Boot Architecture
  * [PRINCIPLE_3_NAME] -> III. Testable and Reliable Software
  * [PRINCIPLE_4_NAME] -> IV. Actionable Data-Quality Analysis
  * [PRINCIPLE_5_NAME] -> V. Privacy-Conscious Healthcare Software
  * [PRINCIPLE_6_NAME] (Added) -> VI. Incremental Development and Simplicity
  * [PRINCIPLE_7_NAME] (Added) -> VII. Developer-Focused API Design
- Added Sections:
  * Technology Stack & Architectural Constraints (replacing SECTION_2)
  * Development Workflow & Quality Gates (replacing SECTION_3)
- Removed Sections: None
- Follow-up TODOs: None
-->

# FHIRLint Constitution

## Core Principles

### I. Standards-First Healthcare Interoperability
FHIRLint MUST utilize established FHIR R4 standards and the HAPI FHIR library rather than implementing custom parsers, serializers, or data models unnecessarily. The application MUST NOT invent behavior, extensions, or validation rules that conflict with established HL7 FHIR R4 specifications or semantics.
*Rationale*: Reimplementing complex medical interoperability standards is error-prone, violates compliance, and defeats the goal of native healthcare system integration.

### II. Maintainable Spring Boot Architecture
The codebase MUST favor a clear separation of concerns (such as controllers, services, repositories) and conventional, idiomatic Spring Boot patterns. Unnecessary abstractions, speculative frameworks, and complex infrastructure (such as message queues or distributed caches) MUST be avoided unless backed by a validated requirement.
*Rationale*: Over-engineering hinders maintainability. Conventional Spring Boot patterns keep the project approachable and clean.

### III. Testable and Reliable Software
All critical quality rules, custom linting engines, and business logic MUST have comprehensive, automated test coverage. Tests SHOULD verify meaningful, outer-loop behavior (such as end-to-end API response checks and validator outputs) rather than fragile, mock-heavy implementation details. The application MUST NOT claim or document any functionality that is not backed by an active, passing automated test.
*Rationale*: High-quality healthcare software requires rock-solid reliability. Ensuring documented features are fully tested prevents regression and false promises of data quality.

### IV. Actionable Data-Quality Analysis
Validation and analysis findings MUST explain clearly what is wrong, where in the resource it occurs (using standard FHIRPath/JSONPath), why the issue matters, and provide a concrete action a developer can take to resolve it. FHIRLint MUST clearly distinguish low-level structural FHIR validation (e.g., schema conformance) from higher-level data-quality, clinical-context, or completeness analysis. Quality scores produced by the system MUST be documented as engineering/integrator indicators rather than clinical, medical, or regulatory compliance measurements.
*Rationale*: Vague linting errors frustrate developers. Providing actionable feedback and keeping quality scores scoped to engineering avoids clinical misunderstandings.

### V. Privacy-Conscious Healthcare Software
All development, demo, and automated test environments MUST utilize exclusively synthetic, de-identified, or non-PHI (Protected Health Information) data. FHIRLint MUST NOT store or expose healthcare data beyond the transient duration required to execute the requested analysis. The system and its documentation MUST explicitly state that FHIRLint is not clinical decision support, does not guarantee clinical correctness, and is not a substitute for clinical validation.
*Rationale*: Healthcare software must treat privacy as a core engineering pillar. Restricting data persistence reduces the risk of accidental exposure and simplifies compliance.

### VI. Incremental Development and Simplicity
Developers MUST build and deliver the Minimum Viable Product (MVP) before adding infrastructure or features that are not explicitly justified by current, verified user requirements. Simpler, monolithic solutions MUST be preferred over premature distributed systems, microservices, or early optimizations. Every new external dependency or infrastructure component (e.g., database, broker) MUST require written justification and approval.
*Rationale*: Premature complexity is the root of most software failure. Keeping things simple preserves speed and keeps the system maintainable.

### VII. Developer-Focused API Design
All REST APIs MUST be predictable, versioned (e.g. in the URI path or headers), fully documented, and return JSON-formatted actionable error payloads. The OpenAPI/Swagger documentation MUST be kept strictly in sync with the actual API implementation using automated validation checks.
*Rationale*: FHIRLint is a developer tool; its interface is its API. Clean, predictable, and accurately documented APIs minimize integration friction.

## Technology Stack & Architectural Constraints
To guarantee consistency and technical safety, FHIRLint MUST adhere to the following stack boundaries:
- **Core Platform**: Java 21+ and Spring Boot.
- **Healthcare Libraries**: HAPI FHIR for parsing, model representation, and baseline conformance validation. Custom code MUST NOT recreate standard FHIR resource parsers or serializers.
- **Data Stores**: PostgreSQL for storing metadata, jobs, and analysis findings. No real PHI is stored.
- **Dependency Control**: External libraries MUST have clear justification. Any additions to `build.gradle` require architectural justification and team review.

## Development Workflow & Quality Gates
Development of FHIRLint is structured around quality and simplicity:
- **No Untested Claims**: No functional capabilities can be documented, announced, or committed without a corresponding automated integration/unit test in the codebase.
- **OpenAPI Schema Consistency**: The OpenAPI/Swagger documentation MUST be verified automatically against the actual endpoints during build verification.
- **Code Review & Standards**: All pull requests must verify compliance with this Constitution and must run the project check task (`./gradlew check`) to ensure no compile errors, linter violations, or failing tests exist.

## Governance
1. **Supremacy**: This Constitution represents the highest engineering and governance authority for the FHIRLint project. All design plans, architecture decision records (ADRs), pull requests, and implementations must comply with the principles outlined herein.
2. **Amendment Procedure**: Amendments to this Constitution require a formal proposal, thorough discussion of alternatives, and a consensus decision by the project maintainers. Any amendment must be recorded by incrementing the version number (using semantic versioning rules) and updating the Last Amended Date.
3. **Compliance Review**: All future specifications, implementation plans, and Pull Requests (PRs) must explicitly reference compliance with these principles. If a design violates or seeks to bypass any of these rules, it must be rejected or the Constitution itself must be amended first.
4. **Tooling & Guidance**: Runtime development decisions, style guidelines, and code linting settings must be kept in sync with these principles. Use `../../PRODUCT_SPEC.md` for specification details.

**Version**: 1.0.0 | **Ratified**: 2026-09-25 | **Last Amended**: 2026-09-25
