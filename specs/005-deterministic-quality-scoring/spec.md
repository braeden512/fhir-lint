# Feature Specification: Phase 5 — Deterministic Multi-Category Quality Scoring Model

**Feature Branch**: `005-deterministic-quality-scoring`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Create the spec for phase 5: deterministic quality scoring model. Use the documentation in docs, specifically the ADR for docs/adr/ADR-005-phase-5-deterministic-quality-scoring-model.md, as well as the PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Defect-Density Category Scoring & Severity Penalties (Priority: P1)

As a healthcare data engineer, I want FHIRLint to compute deterministic, volume-normalized quality scores (0–100) for each of the six quality categories (`structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`), applying severity penalties (15 points for an error, 3 points for a warning, 0 points for informational notices) scaled against total resource volume, so that small datasets with defects are penalized appropriately while large datasets are not unfairly brought to zero by isolated warnings.

**Why this priority**: Scoring categories in isolation provides essential diagnostic granularity. A dataset might have flawless structural syntax but broken relational links or inverted timestamps. Calculating per-category defect density normalized by total resource count guarantees that quality assessment scales fairly across single-resource bundles and enterprise datasets containing tens of thousands of resources.

**Independent Test**: Can be tested independently by submitting synthetic issue sets with known resource counts to the scoring engine and verifying that each category calculates the exact penalty, density factor, and clamped category score (0–100) matching the mathematical formula defined in ADR-005.

**Acceptance Scenarios**:

1. **Given** a dataset with 10 total resources and zero diagnostic issues across all categories, **When** category scoring is performed, **Then** the system computes a `CategoryScore` of 100 for all six categories (`structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`).
2. **Given** a dataset with 10 total resources containing 1 error issue in the `referentialIntegrity` category, **When** category scoring is performed, **Then** the system computes a defect penalty of 15 ($1 \times 15$), a density factor of $15 / (10 \times 15) = 0.10$, and a `CategoryScore` of 90 for `referentialIntegrity`.
3. **Given** a dataset with 10 total resources containing 2 warning issues in the `terminology` category, **When** category scoring is performed, **Then** the system computes a defect penalty of 6 ($2 \times 3$), a density factor of $6 / (10 \times 15) = 0.04$, and a `CategoryScore` of 96 for `terminology`.
4. **Given** a dataset with any number of informational (`INFO`) issues in a category, **When** category scoring is performed, **Then** informational issues incur 0 penalty points and do not reduce the category score.
5. **Given** a dataset where the defect penalty in a category exceeds the maximum volume-based threshold (density factor $\ge 1.0$), **When** category scoring is performed, **Then** the category score is clamped to 0 (never negative).
6. **Given** duplicate entity findings (rules originating from identity and duplicate detection), **When** category scoring is performed, **Then** the findings are evaluated under the `consistency` category score.

---

### User Story 2 - Weighted Overall Composite Score & Engineering Grade Classification (Priority: P2)

As a pipeline developer or release manager, I want FHIRLint to combine the category scores into a single weighted composite overall quality score (0–100) using fixed, documented category weights and assign a standardized engineering grade tier (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`), so that automated pipelines and engineering teams can immediately assess ingestion safety and dataset hygiene.

**Why this priority**: An overall score and human-readable grade tier provides an immediate, decisive signal for automated pipelines and engineering dashboards, indicating whether a clinical dataset is safe for downstream processing or represents a corrupted payload that must be rejected.

**Independent Test**: Can be tested independently by supplying pre-calculated category scores to the aggregation engine and verifying that the overall score computes the exact rounded weighted sum and assigns the expected grade tier according to the documented thresholds.

**Acceptance Scenarios**:

1. **Given** category scores across all six dimensions, **When** the composite overall score is calculated, **Then** the system computes the weighted sum using:
   - Referential Integrity: 25% ($w = 0.25$)
   - Structural Conformance: 20% ($w = 0.20$)
   - Profile Conformance: 20% ($w = 0.20$)
   - Consistency: 15% ($w = 0.15$)
   - Terminology: 10% ($w = 0.10$)
   - Completeness: 10% ($w = 0.10$)
   and rounds the result to the nearest integer.
2. **Given** an overall score between 90 and 100 (inclusive), **When** grade classification is assigned, **Then** the system assigns the grade `EXCELLENT` ("High integrity, safe for automated ingestion").
3. **Given** an overall score between 75 and 89 (inclusive), **When** grade classification is assigned, **Then** the system assigns the grade `ACCEPTABLE` ("Minor non-critical warnings; downstream review recommended").
4. **Given** an overall score between 50 and 74 (inclusive), **When** grade classification is assigned, **Then** the system assigns the grade `DEGRADED` ("Contains broken references or chronological inconsistencies").
5. **Given** an overall score between 0 and 49 (inclusive), **When** grade classification is assigned, **Then** the system assigns the grade `CRITICAL` ("Severe structural or relational failures; ingestion should halt").
6. **Given** identical datasets and issue lists evaluated multiple times, **When** scoring is executed repeatedly, **Then** the scoring engine produces identical numeric scores and identical grade tiers on every execution (100% deterministic reproducibility).

---

### User Story 3 - Explainable Score Breakdown & Deduction Traceability (Priority: P3)

As a developer diagnosing a degraded dataset score, I want FHIRLint to provide a comprehensive, transparent score breakdown showing the exact issue counts (errors, warnings, infos), defect penalties, density factors, and point contributions for each category, along with an explicit engineering-indicator disclaimer, so that every point deduction is traceable and clinical misunderstandings are prevented.

**Why this priority**: Opaque, arbitrary scores frustrate developers. Explaining exactly how each issue contributed to score reductions builds developer trust and guides rapid remediation. Furthermore, Constitution Principle IV mandates that scores be explicitly documented as engineering and data integration indicators, rather than clinical, medical, or regulatory compliance measurements.

**Independent Test**: Can be tested independently by evaluating a dataset with varied issues across multiple categories and verifying that the resulting score breakdown exposes all contributing metrics and includes the required non-clinical disclaimer.

**Acceptance Scenarios**:

1. **Given** a dataset evaluated by FHIRLint, **When** the scoring breakdown is generated, **Then** the breakdown provides for each of the six categories:
   - Category name and unique key
   - Configured weight percentage
   - Total count of errors, warnings, and informational notices
   - Calculated defect penalty points
   - Volume density factor
   - Resulting category score (0–100)
2. **Given** any score deduction in a category, **When** an engineer inspects the breakdown, **Then** the total penalty points equal $(N_{\text{error}} \times 15) + (N_{\text{warning}} \times 3)$, with every deduction directly traceable to the specific diagnostic issues reported by the validation phases.
3. **Given** any generated quality score summary or report, **When** the score is presented, **Then** the system includes an explicit non-clinical engineering indicator disclaimer stating that the score measures technical data hygiene and engineering integrity, and is not a clinical, diagnostic, or regulatory certification.

---

### User Story 4 - Quality Gate Threshold Evaluation (Priority: P4)

As a DevOps engineer configuring automated CI/CD pipelines, I want the scoring model to evaluate configurable quality gate criteria (such as minimum required overall score `--min-score` and maximum permissible issue severity `--fail-on`), producing a definitive pass/fail verdict with clear gate breach descriptions, so that automated build pipelines can reliably enforce data quality standards.

**Why this priority**: Quality scores become actionable in continuous integration when they can automatically trigger or prevent downstream deployments or data imports based on explicit engineering policies.

**Independent Test**: Can be tested independently by evaluating datasets against different gate configurations (e.g. `--min-score 85`, `--fail-on error`, `--fail-on warning`) and verifying that the gate evaluation correctly reports pass or fail with appropriate failure diagnostic messages.

**Acceptance Scenarios**:

1. **Given** a quality gate configured with a minimum score threshold (e.g., `--min-score 80`), **When** the dataset achieves an overall score of 84, **Then** the quality gate passes.
2. **Given** a quality gate configured with `--min-score 80`, **When** the dataset achieves an overall score of 79, **Then** the quality gate fails and reports the gate breach with the actual score and the required threshold.
3. **Given** a quality gate configured with `--fail-on error`, **When** the dataset has an overall score of 95 but contains 1 `ERROR` issue, **Then** the quality gate fails and identifies the presence of an unpermitted error severity issue.
4. **Given** a quality gate configured with `--fail-on warning`, **When** the dataset has 0 errors but contains 1 `WARNING` issue, **Then** the quality gate fails and identifies the presence of an unpermitted warning severity issue.
5. **Given** a quality gate configured with `--min-score 70` and `--fail-on error`, **When** the dataset achieves an overall score of 85 with 0 errors and 2 warnings, **Then** the quality gate passes.
6. **Given** a quality gate configured with both `--min-score 80` and `--fail-on error`, **When** the dataset achieves an overall score of 65 and contains 1 `ERROR` issue, **Then** the quality gate fails and captures all applicable breach descriptions (both the score threshold breach and the severity violation without premature short-circuiting).

---

### Edge Cases

- **Zero Resources (Empty Dataset)**: If a dataset contains 0 resources, the density factor formula uses $\max(N_{\text{totalResources}}, 1) = 1$ to prevent division by zero. If zero issues are present, all category scores and overall score evaluate to 100.
- **Single Resource Dataset with Multiple Errors**: In a single-resource dataset ($N = 1$), a single error incurs a penalty of 15 and a density factor of $15 / 15 = 1.0$, reducing the category score to 0. Multiple errors in that category will not drive the score below 0 (clamped strictly at 0).
- **Extreme Defect Density**: When thousands of issues occur in a dataset (e.g., defect penalty far exceeding $N \times 15$), the density factor exceeds 1.0. The clamping mechanism $\max(0, 100 \times (1 - \min(1.0, \text{DensityFactor})))$ ensures the category score remains bounded at 0 and never produces negative values.
- **Floating-Point Determinism & Rounding**: Rounding for category scores and the overall composite score uses standard deterministic half-up rounding (`round(x)` where $0.5$ rounds up to $1.0$). Intermediate calculations maintain double precision to prevent accumulation of rounding errors.
- **Informational Issues Only**: Datasets containing only `INFO` severity notices experience 0 defect penalty, resulting in a 100 overall score and `EXCELLENT` grade tier, and do not trigger quality gate failures under `--fail-on error` or `--fail-on warning`.
- **Category Issue Categorization**: Every diagnostic issue produced by upstream phases (syntactic/structural, profile conformance, referential integrity, consistency, duplicate detection, terminology, completeness) must map cleanly to one of the six canonical categories without unassigned issues. Specifically, duplicate detection issues (`DUP-` rules) map to the `consistency` category.
- **Zero-Retention Stateless Operation**: All scoring structures and calculation results are held strictly in-memory during evaluation and never persisted to local databases or external systems, preserving Constitution Principles II and V.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST calculate quality scores for six distinct categories: `structural` (Structural Conformance), `profileConformance` (Profile Conformance), `referentialIntegrity` (Referential Integrity), `consistency` (Cross-Resource Consistency & Duplicates), `terminology` (Terminology), and `completeness` (Data Completeness).
- **FR-002**: System MUST compute the category defect penalty using the exact formula: $\text{DefectPenalty}_c = (N_{\text{error}, c} \times 15) + (N_{\text{warning}, c} \times 3) + (N_{\text{info}, c} \times 0)$.
- **FR-003**: System MUST compute the category defect density factor normalized against total resource count: $\text{DensityFactor}_c = \frac{\text{DefectPenalty}_c}{\max(N_{\text{totalResources}}, 1) \times 15}$.
- **FR-004**: System MUST compute the category score clamped between 0 and 100 using: $\text{CategoryScore}_c = \text{round}\Big(\max\big(0, 100 \times (1 - \min(1.0, \text{DensityFactor}_c))\big)\Big)$.
- **FR-005**: System MUST compute the overall quality score as a weighted sum of category scores: $\text{OverallScore} = \text{round}\left( \sum_{c} w_c \cdot \text{CategoryScore}_c \right)$, using the default weights:
  - Referential Integrity: 25% ($w = 0.25$)
  - Structural Conformance: 20% ($w = 0.20$)
  - Profile Conformance: 20% ($w = 0.20$)
  - Consistency: 15% ($w = 0.15$)
  - Terminology: 10% ($w = 0.10$)
  - Completeness: 10% ($w = 0.10$).
- **FR-006**: System MUST assign an engineering grade tier based on the overall quality score:
  - `EXCELLENT`: Overall score 90–100
  - `ACCEPTABLE`: Overall score 75–89
  - `DEGRADED`: Overall score 50–74
  - `CRITICAL`: Overall score 0–49.
- **FR-007**: System MUST provide an explainable score breakdown exposing the category weight, error count, warning count, info count, raw defect penalty, density factor, and resulting score for each of the six categories.
- **FR-008**: System MUST include a mandatory, canonical non-clinical engineering indicator disclaimer (`Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements.`) across all quality score outputs.
- **FR-009**: System MUST evaluate quality gate criteria based on optional minimum score thresholds (`minScore`, 0–100) and optional fail-on severity thresholds (`failOn`, `ERROR` or `WARNING`).
- **FR-010**: System MUST report a quality gate pass if the overall score meets or exceeds `minScore` (when specified) and no detected issues match or exceed `failOn` severity (when specified).
- **FR-011**: System MUST report a quality gate failure capturing all applicable breach descriptions (without early short-circuiting) if the overall score falls below `minScore` or any issue violates the `failOn` severity constraint.
- **FR-012**: System MUST guarantee 100% deterministic reproducibility: executing scoring with identical resource counts and issue sets MUST produce identical category scores, overall scores, and grade tiers across runs.
- **FR-013**: System MUST map diagnostic issues with category `DUPLICATE` into the `consistency` category for scoring calculation.
- **FR-014**: System MUST perform all score calculations completely in-memory with zero external network connectivity, zero database persistence, and zero disk caching.
- **FR-015**: System MUST execute scoring calculations in under 5 milliseconds for datasets containing up to 100,000 resources and 10,000 issues.
- **FR-016**: System MUST provide programmatic access to the scoring engine through an immutable, strongly-typed model suitable for consumption by downstream CLI formatters and embeddable library callers.

### Key Entities

- **Quality Score**: The aggregate outcome of quality assessment, containing the overall composite score (0–100), the assigned engineering grade tier, the category score breakdown, the quality gate evaluation result, and the non-clinical engineering indicator disclaimer.
- **Category Score**: The calculated quality rating for a specific functional dimension (0–100), containing the category enum/identifier, weight, issue counts by severity, total defect penalty, density factor, and final score.
- **Scoring Category**: The six enumerated quality dimensions: `STRUCTURAL`, `PROFILE_CONFORMANCE`, `REFERENTIAL_INTEGRITY`, `CONSISTENCY`, `TERMINOLOGY`, and `COMPLETENESS`.
- **Engineering Grade**: The standardized four-tier rating scale: `EXCELLENT` (90–100), `ACCEPTABLE` (75–89), `DEGRADED` (50–74), and `CRITICAL` (0–49), with descriptive engineering status summaries.
- **Quality Gate Config**: The configuration parameters governing pass/fail policy enforcement, including minimum passing score (0–100) and severity failure thresholds (`NONE`, `WARNING`, `ERROR`).
- **Quality Gate Result**: The policy outcome of quality gate evaluation, indicating whether the evaluation passed or failed, accompanied by specific breach reason descriptions.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of synthetic test scenarios matching ADR-005 category penalty and density factor formulas produce the exact expected category score (0–100).
- **SC-002**: 100% of composite score calculations across all combinations of category scores produce the exact weighted sum matching the specified category weights ($0.25, 0.20, 0.20, 0.15, 0.10, 0.10$) and round half-up deterministically.
- **SC-003**: 100% of overall scores map to the exact documented engineering grade tier (`EXCELLENT` for 90–100, `ACCEPTABLE` for 75–89, `DEGRADED` for 50–74, `CRITICAL` for 0–49) without boundary off-by-one errors.
- **SC-004**: 100% of runs with identical inputs produce identical scores and grades across 10,000 consecutive automated test iterations (zero non-deterministic drift).
- **SC-005**: Scoring calculation execution for a benchmark suite of 100,000 resources and 10,000 diagnostic issues completes in under 5 milliseconds on standard developer hardware.
- **SC-006**: 100% of score breakdowns provide mathematical traceability showing category weights, issue counts by severity, penalty points, density factor, and category score for all six categories.
- **SC-007**: 100% of quality gate evaluations correctly report `PASSED` or `FAILED` matching configured `--min-score` and `--fail-on` criteria.
- **SC-008**: 100% of generated quality score outputs include the mandatory non-clinical engineering indicator disclaimer mandated by Constitution Principle IV.
- **SC-009**: 0% negative category or overall scores emitted across boundary edge cases (including datasets with defect densities exceeding 100x the resource volume).
- **SC-010**: Zero external database, disk, or network dependencies utilized during score calculation.

---

## Assumptions

- Diagnostic issues analyzed by the scoring engine are produced by upstream validation and rule evaluation phases (Phase 1, Phase 2, Phase 3, and Phase 4), each providing an assigned severity (`ERROR`, `WARNING`, `INFO`) and quality category.
- Total resource count analyzed by the ingestion lifecycle (Phase 1) is passed to the scoring engine as volume context for density normalization.
- In accordance with ADR-005 and PRODUCT_SPEC.md, duplicate entity issues (`DUP-` rules) are scored under the `consistency` category.
- Default category weights sum to exactly 1.0 (100%): Referential Integrity (0.25), Structural Conformance (0.20), Profile Conformance (0.20), Consistency (0.15), Terminology (0.10), Completeness (0.10).
- If custom weights or thresholds are supported in the future, validation will enforce that category weights sum to 1.0 and are non-negative.
- Half-up rounding (`round(x)`) is the standard mathematical convention for all integer scores.
- Quality scores represent engineering and data integration indicators for technical hygiene, and are never used as medical or clinical diagnostic measurements (Constitution Principle IV).
- The scoring engine executes in-memory with strict zero data retention (Constitution Principles II and V).
