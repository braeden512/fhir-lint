# Implementation Plan: Phase 5 — Deterministic Multi-Category Quality Scoring Model

**Branch**: `005-deterministic-quality-scoring` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-deterministic-quality-scoring/spec.md`

## Summary

Implement the deterministic multi-category quality scoring model and CI/CD quality gate engine for FHIRLint. The model converts raw diagnostic findings (from structural parsing, profile validation, referential integrity, and data quality rules) into volume-normalized category scores across six canonical quality dimensions (`structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`). It computes a weighted composite overall score (0–100), assigns a four-tier engineering grade (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`), exposes complete deduction explainability via `CategoryScoreDetail`, embeds a mandatory non-clinical engineering indicator disclaimer, maps duplicate entity findings directly to consistency (FR-013), and evaluates configurable quality gate policies (`--min-score`, `--fail-on`) with multi-breach diagnostics.

---

## Technical Context

**Language/Version**: Java 21+

**Primary Dependencies**:
- HAPI FHIR R4 (`hapi-fhir-base:6.10.0`, `hapi-fhir-structures-r4:6.10.0`, `hapi-fhir-validation:6.10.0`)
- Picocli 4.7.6 (CLI parsing, options, exit codes)
- Jackson 2.18.2 (JSON report serialization)
- SLF4J 2.0.16 + Logback 1.5.16 (Logging)

**Storage**: None (Strictly stateless in-memory execution; zero database, zero disk cache, zero PHI retention)

**Testing**: JUnit 5 (5.11.4), AssertJ (3.27.3)

**Target Platform**: Cross-platform Linux / macOS / Windows on JVM 21+

**Project Type**: Standalone CLI tool and embeddable pure Java library (`fhir-lint-core`)

**Performance Goals**:
- Scoring execution completes in < 5 milliseconds for 100,000 resources and 10,000 issues on standard developer hardware
- Single $O(I + C)$ linear pass over issue list
- Zero memory leakage and minimal working memory overhead (< 1 MB)

**Constraints**:
- 100% deterministic reproducibility: identical inputs produce identical scores and grades across runs
- Strict zero-retention privacy: all scoring state held transiently in memory
- Pure Java 21: zero external scoring or rules frameworks
- Explicit non-clinical engineering disclaimer embedded in all score representations

**Scale/Scope**:
- 6 canonical scoring categories
- 4 engineering grade tiers
- Quality gate policy evaluation supporting `--min-score` and `--fail-on`
- Multi-breach diagnostic capture without short-circuiting

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Evaluation |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | PASS | Operates directly on HAPI FHIR R4 domain models and existing diagnostic findings from standard validation phases without inventing conflicting semantics. |
| **II. Lean, Dependency-Minimized Architecture** | PASS | Pure Java 21 records and classes (`QualityScore`, `CategoryScoreDetail`, `QualityGateConfig`, `QualityGateResult`) with zero external math or rule libraries. Completely stateless and in-memory. |
| **III. Testable and Reliable Software** | PASS | Comprehensive automated tests verify defect penalty formulas, density factors, category clamping, weighted overall score, grade assignment, gate evaluation, and 10,000-run non-deterministic drift assertions. |
| **IV. Actionable Data-Quality Analysis** | PASS | Provides full explainability of point deductions per category (`CategoryScoreDetail`) and includes the mandatory canonical disclaimer documenting scores as engineering/integrator indicators rather than clinical/medical certifications. |
| **V. Privacy-Conscious Healthcare Software** | PASS | Strictly in-memory computation with zero persistence, zero disk caching, zero telemetry, and exclusive use of synthetic datasets in tests. |
| **VI. Incremental Development and Simplicity** | PASS | Implements the exact scoring model and quality gates defined for Phase 5. Advanced dataset diffing and custom user-defined formula scripting deferred to Phase 8. |
| **VII. Developer-Focused CLI & Library Design** | PASS | Exposes strongly typed, immutable Java APIs for library callers and integrates seamlessly into Picocli CLI exit code contracts (`0` on pass, `1` on gate failure). |

---

## Project Structure

### Documentation (this feature)

```text
specs/005-deterministic-quality-scoring/
├── plan.md              # This implementation plan
├── research.md          # Phase 0: Technical decisions and research findings
├── data-model.md        # Phase 1: Entity definitions, fields, and relationships
├── quickstart.md        # Phase 1: Runnable end-to-end verification guide
├── contracts/           # Phase 1: Interface contracts
│   ├── scoring-engine-api.md
│   └── quality-gate-contract.md
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
src/
├── main/java/org/fhirlint/
│   ├── cli/
│   │   ├── FhirLintApplication.java
│   │   ├── command/
│   │   │   └── ValidateCommand.java
│   │   └── renderer/
│   │       ├── ConsoleTableRenderer.java
│   │       ├── JsonReportRenderer.java
│   │       └── SarifReportRenderer.java
│   └── core/
│       ├── FhirLinter.java
│       └── model/
│           ├── CategoryScoreDetail.java   # [NEW] Rich category breakdown record
│           ├── IssueCategory.java         # [REFINED] Category weights, isScored(), and duplicate mapping (FR-013)
│           ├── LintReport.java            # [UPDATED] Delegates gate evaluation; preserves passes(int, Severity) overload
│           ├── QualityGateConfig.java     # [NEW] Configuration for min-score and fail-on
│           ├── QualityGateResult.java     # [NEW] Pass/fail verdict with breach descriptions
│           └── QualityScore.java          # [REFINED] Scoring logic, Grade enum, disclaimer, 6-parameter overload
└── test/java/org/fhirlint/
    ├── core/
    │   ├── QualityScoreTest.java          # [EXPANDED] Comprehensive tests for formulas, weights, grades
    │   └── QualityGateTest.java           # [NEW] Unit tests for gate evaluation and multi-breaches
    └── cli/
        └── QualityGateCliTest.java        # [NEW] CLI exit code tests for --min-score and --fail-on
```

**Structure Decision**: Standard single Java project structure adhering to existing package boundaries in `org.fhirlint.core.model`, `org.fhirlint.cli`, and `src/test/java/org/fhirlint`.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations. All design choices strictly adhere to Constitution v2.0.0 principles.*
