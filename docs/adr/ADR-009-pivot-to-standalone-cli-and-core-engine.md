# ADR-009: Pivot to Zero-Infrastructure Standalone CLI and Core Java Engine

## Status
Accepted (Amending ADR-001, ADR-006, and ADR-007)

## Context & Problem Statement
The original architecture of FHIRLint (defined in ADR-001) positioned the project as a hosted, multi-tenant Spring Boot REST API backed by a PostgreSQL database and asynchronous background worker queues.

During Phase 0 and Phase 1, two fundamental operational realities emerged:
1. **Healthcare Data Privacy & Procurement Barriers**: In real-world digital health, covered entities and health systems cannot easily transmit clinical data across the internet to a third-party hosted API due to HIPAA compliance, Business Associate Agreement (BAA) requirements, SOC 2 audits, and institutional security reviews. Even with zero-retention policies, external network egress of PHI is often strictly forbidden.
2. **Infrastructure and Hosting Costs**: Maintaining an externally accessible cloud service with high-memory JVM instances, PostgreSQL databases, load balancers, and autoscaling queues imposes substantial ongoing hosting costs ($ hundreds/month) on the maintainer.
3. **Developer Ergonomics**: Developers and data engineering teams need linters (like ESLint, Ruff, Spectral, or Hadolint) to run locally in their terminals, in pre-commit hooks, and in self-hosted CI/CD pipelines (such as GitHub Actions) with standard UNIX pipes and exit codes.

## Decision Drivers
1. **Zero Infrastructure Cost**: $0 operational cost for the maintainer.
2. **Complete Data Privacy**: Healthcare data never leaves the user's local machine or private CI/CD runner.
3. **Speed & Simplicity**: Instant in-memory execution without booting web servers, Docker containers, or database engines.
4. **Developer Workflows**: Seamless integration with CI/CD gates (`exit 0` on pass, `exit 1` on quality defects), ANSI colorized terminal output, and SARIF GitHub integration.
5. **Universal Accessibility**: Enable non-Java teams (Python, TypeScript, Go) to run FHIRLint via a standalone CLI executable.

## Considered Options
1. **Status Quo (Hosted Spring Boot REST API + PostgreSQL)**: High hosting costs, heavy procurement barriers for users, unnecessary operational complexity.
2. **Local Spring Boot Docker Container**: Still requires running Docker daemons and PostgreSQL containers locally on the user's laptop; heavy startup latency and resource overhead for simple linting tasks.
3. **Standalone Picocli CLI + Pure Java Engine (Zero Database, Zero Web Framework)**: The tool is packaged as a standalone executable CLI and embeddable Java library. HAPI FHIR performs in-memory parsing and validation. Tests and runs execute in milliseconds with zero dependencies.

## Decision Outcome
Adopt **Option 3: Standalone Picocli CLI + Pure Java Engine**.

### Architecture Overview
1. **`fhir-lint-core`**: A lightweight, framework-agnostic Java 21 library. Uses HAPI FHIR R4 for parsing and profile validation, builds in-memory resource graphs, executes pluggable quality rules, and computes deterministic scores. It has zero dependencies on Spring Boot, JPA, or PostgreSQL.
2. **`fhir-lint-cli`**: A command-line application powered by Picocli.
   - Command: `fhir-lint validate <file|dir|-> [options]`
   - Options: `--profile`, `--format table|json|sarif`, `--min-score`, `--fail-on error|warning`
   - Exit codes: `0` for pass, `1` for quality failure, `2` for syntax/file errors.
3. **Packaging Strategy**:
   - Runnable Fat JAR (`java -jar fhir-lint.jar ...`)
   - GraalVM Native Image compilation to a single standalone OS binary with zero JVM prerequisite.
   - GitHub Action runner for CI/CD gates.

## Consequences
### Positive
- Hosting cost drops from recurring cloud expenses to **$0**.
- Complete elimination of HIPAA liability: no data is ever transmitted or persisted.
- Execution speed: sub-second execution directly against local files or standard input.
- Clean, maintainable architecture aligned with Constitution Principle II and VI.

### Negative / Trade-offs
- Removing the HTTP REST API means non-Java systems invoke FHIRLint via the CLI / subprocess or GitHub Action rather than via HTTP calls over a network. This is the standard pattern for developer linters (e.g. ESLint, Ruff, Hadolint).
