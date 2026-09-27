# ADR-008: Phase 8 — Advanced Extensibility, CI/CD Integration, and Dataset Comparison

## Status
Proposed (Future Phase, Aligned with ADR-009)

## Context & Problem Statement
Once the core FHIRLint platform, rules engine, DX, and standalone packaging are operating reliably, enterprise users will demand advanced capabilities:
1. Running quality checks in automated CI/CD deployment pipelines to block invalid synthetic or exported bundles.
2. Comparing two healthcare datasets (`fhir-lint compare baseline.json target.json`) to track quality regressions across versions.
3. Defining organization-specific custom rules via YAML or script without recompiling the Java application.

## Decision Drivers
1. **Developer Integration**: Direct integration into GitHub Actions, GitLab CI, and pre-commit hooks.
2. **Quality Trend Tracking**: Visualizing whether data quality is improving or deteriorating between dataset releases.
3. **No Premature Complexity**: Keep advanced features modular so they do not destabilize the MVP core.

## Considered Options
1. **CLI Comparison & YAML Dynamic Rules**: Implemented as pure CLI commands (`fhir-lint compare`) and dynamic FHIRPath evaluator in `fhir-lint-core`. Zero external network or web server requirements.
2. **Heavy Distributed Workflow Engine (Temporal/Airflow)**: Excessive complexity for local data comparison.

## Decision Outcome
Adopt **Option 1: CLI Subcommands and Dynamic FHIRPath Rules**.

### Architecture Blueprint
1. **CI/CD Quality Gate**:
   - CLI flags: `--fail-on error`, `--min-score 85`.
   - Returns exit code 1 if thresholds are violated, cleanly failing CI builds.
2. **Dataset Comparison (`fhir-lint compare baseline.json target.json`)**:
   - Accepts two Bundle files or directories.
   - Compares:
     - Resource count deltas
     - Score differential ($\Delta \text{score}$)
     - Fixed issues vs newly introduced regressions.
     - Formatted comparison table or JSON diff.
3. **Dynamic FHIRPath Rule Definitions**:
   - Allow users to supply custom YAML rule definitions (`--rules custom-rules.yaml`) containing FHIRPath invariant expressions evaluated dynamically by HAPI's FHIRPath engine.

## Consequences
### Positive
- Expands FHIRLint into a continuous regression testing tool for healthcare data engineering.
- Unlocks enterprise CI/CD adoption.
- Protects the MVP from premature complexity by deferring implementation to Phase 8.

### Negative / Trade-offs
- Dynamic FHIRPath evaluation may have a small runtime performance overhead compared to compiled Java rules.
