# ADR-005: Phase 5 — Deterministic Multi-Category Quality Scoring Model

## Status
Accepted

## Context & Problem Statement
A major complaint of linting tools is arbitrary, opaque scoring ("Your code is 63%"). In healthcare data engineering, developers need a transparent, mathematically deterministic score that reflects both the **breadth** (categories of quality) and **severity** (fatal flaws vs minor warnings) of issues.

Furthermore, Constitution Principle IV dictates: "Quality scores produced by the system MUST be documented as engineering/integrator indicators rather than clinical, medical, or regulatory compliance measurements."

## Decision Drivers
1. **Explainability**: Every point deduction must be traceable to specific detected issues.
2. **Deterministic Reproducibility**: Running the exact same dataset must produce the exact same score every time.
3. **Multi-Category Granularity**: Developers must see which specific dimension is failing (e.g., perfect structural validity, but terrible referential integrity).
4. **Volume Normalization**: A single warning in a 10-resource bundle should penalize more heavily than a single warning in a 10,000-resource bundle.

## Considered Options
1. **Binary Pass/Fail**: Too simplistic; doesn't indicate degree of data hygiene.
2. **Fixed Subtraction Model**: Subtract fixed points (e.g., -5 per error) until 0. (Breaks down on large datasets where 20 errors in 10,000 resources drops score to 0).
3. **Defect-Density Weighted Category Model**: Calculates category scores based on defect density normalized against resource volume, then computes a weighted overall score.

## Decision Outcome
Adopt **Option 3: Defect-Density Weighted Category Model**.

### Mathematical Model

#### 1. Category Penalty & Score
For each category $c \in \{\text{structural}, \text{profileConformance}, \text{referentialIntegrity}, \text{consistency}, \text{terminology}, \text{completeness}\}$:

$$\text{DefectPenalty}_c = (N_{\text{error}, c} \times 15) + (N_{\text{warning}, c} \times 3) + (N_{\text{info}, c} \times 0)$$

$$\text{DensityFactor}_c = \frac{\text{DefectPenalty}_c}{\max(N_{\text{totalResources}}, 1) \times 15}$$

$$\text{CategoryScore}_c = \text{round}\Big(\max\big(0, 100 \times (1 - \min(1.0, \text{DensityFactor}_c))\big)\Big)$$

#### 2. Category Weighting
The overall quality score is a weighted composite of the category scores:
- **Referential Integrity**: 25% ($w = 0.25$)
- **Structural Conformance**: 20% ($w = 0.20$)
- **Profile Conformance**: 20% ($w = 0.20$)
- **Consistency**: 15% ($w = 0.15$)
- **Terminology**: 10% ($w = 0.10$)
- **Completeness**: 10% ($w = 0.10$)

$$\text{OverallScore} = \text{round}\left( \sum_{c} w_c \cdot \text{CategoryScore}_c \right)$$

#### 3. Engineering Grade Thresholds
- **90–100**: `EXCELLENT` — High integrity, safe for automated ingestion.
- **75–89**: `ACCEPTABLE` — Minor non-critical warnings; downstream review recommended.
- **50–74**: `DEGRADED` — Contains broken references or chronological inconsistencies.
- **0–49**: `CRITICAL` — Severe structural or relational failures; ingestion should halt.

## Consequences
### Positive
- Fully reproducible and explainable.
- Scales gracefully with both tiny bundles and large enterprise datasets.
- Clear documentation for engineers explaining why their score changed between runs.

### Negative / Trade-offs
- Weightings are subjective engineering choices, though clearly documented and configurable in `application.yml`.
