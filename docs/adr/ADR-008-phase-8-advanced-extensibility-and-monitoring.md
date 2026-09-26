# ADR-008: Phase 8 — Advanced Extensibility, CI/CD Integration, and Dataset Comparison

## Status
Proposed (Future Phase)

## Context & Problem Statement
Once the core FHIRLint platform, rules engine, DX, and production pipelines are operating reliably, enterprise users will demand advanced capabilities:
1. Running quality checks in automated CI/CD deployment pipelines to block invalid synthetic or exported bundles.
2. Comparing two healthcare datasets (`POST /api/v1/compare`) to track quality regressions across versions.
3. Defining organization-specific custom rules without recompiling the Java application.
4. Receiving webhook notifications when asynchronous batch linting jobs complete.

## Decision Drivers
1. **Developer Integration**: Direct integration into GitHub Actions, GitLab CI, and health system pipelines.
2. **Quality Trend Tracking**: Visualizing whether data quality is improving or deteriorating over time.
3. **No Premature Complexity**: Keep advanced features modular so they do not destabilize the MVP core.

## Considered Options
1. **External CI Runner + Diff Engine + Webhook Dispatcher**: Layered on top of the existing Spring Boot REST API.
2. **Heavy Distributed Workflow Engine (Temporal/Airflow)**: Excessive complexity for data comparison and webhooks.

## Decision Outcome
Adopt **Option 1: Lightweight layered extensions over existing REST API**.

### Architecture Blueprint
1. **CI/CD Quality Gate**:
   - CLI flags: `--fail-on error`, `--min-score 85`.
   - Returns exit code 1 if thresholds are violated, cleanly failing CI builds.
2. **Dataset Comparison (`POST /api/v1/compare`)**:
   - Accepts two Bundle IDs or payloads (`baseline` vs `target`).
   - Compares:
     - Resource count deltas
     - Score differential ($\Delta \text{score}$)
     - Fixed issues vs newly introduced regressions.
3. **Dynamic FHIRPath Rule Definitions**:
   - Allow users to supply custom YAML rule definitions containing FHIRPath invariant expressions evaluated dynamically by HAPI's FHIRPath engine.
4. **Webhook Dispatcher**:
   - If `webhookUrl` is provided in the initial quality-check request, a Spring `RestClient` asynchronously posts the completion payload with retry logic.

## Consequences
### Positive
- Expands FHIRLint into a continuous data-quality monitoring platform.
- Unlocks enterprise CI/CD adoption.
- Protects the MVP from premature complexity by deferring implementation to Phase 8.

### Negative / Trade-offs
- Webhooks introduce network egress considerations and retry/backoff failure handling.
