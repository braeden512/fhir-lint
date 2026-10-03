# Research & Architecture Decisions: Phase 8 — Advanced Extensibility, Dataset Comparison, and Dynamic FHIRPath Rules

**Branch**: `008-advanced-extensibility-diffing` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

---

## 1. Issue Identity Matching Key for Regression Diffing

### Context & Problem
When diffing two dataset lint reports, the engine must distinguish between:
1. **Persistent Issues**: Present in both baseline and target.
2. **Newly Introduced Regressions**: Present in target, absent in baseline.
3. **Resolved Issues**: Present in baseline, absent in target.

In FHIR datasets, multiple distinct issues can exist on the same resource under the same rule (e.g. `REF-001` flagging both `Observation.subject` and `Observation.performer`). Furthermore, some issues (such as `DUP-001` duplicate patient detection or top-level bundle errors) have no `resourceId`.

### Decision
Define a canonical immutable `IssueIdentityKey`:
```java
public record IssueIdentityKey(
    String ruleId,
    String resourceType,
    String resourceId,
    String path,
    int messageHash
) {
    public static IssueIdentityKey of(QualityIssue issue) {
        String resId = issue.resourceId() != null ? issue.resourceId() : "__NO_ID__";
        String p = issue.path() != null ? issue.path() : "__ROOT__";
        int msgHash = resId.equals("__NO_ID__") ? issue.message().hashCode() : 0;
        return new IssueIdentityKey(
            issue.ruleId(),
            issue.resourceType() != null ? issue.resourceType() : "__GLOBAL__",
            resId,
            p,
            msgHash
        );
    }
}
```

### Rationale
- Includes `path` to cleanly distinguish multiple issues on the same resource under the same rule.
- Uses `messageHash` fallback only when `resourceId` is missing (`__NO_ID__`), ensuring deterministic matching without colliding unrelated bundle-level issues.
- Strictly `O(N + M)` set operations using `java.util.HashSet<IssueIdentityKey>`.

### Alternatives Considered
- **Matching on `(ruleId, resourceType, resourceId)` only**: Rejected because multiple issues on different fields of the same resource collide.
- **Full Issue `equals()`**: Rejected because minor description text changes or line numbers would falsely classify an issue as a "new regression" rather than persistent.

---

## 2. In-Memory Dynamic FHIRPath Rule Evaluation

### Context & Problem
Custom rules in YAML specify an invariant boolean FHIRPath expression (e.g. `Observation.status = 'final'` or `component.count() = 2`). The engine must evaluate these invariants against target resources in-memory at high speed (< 1s for 1,000 resources) without spawning subshells or compiling byte-code.

### Decision
Leverage HAPI FHIR's native `FhirPathR4` (`org.hl7.fhir.r4.utils.FHIRPathEngine`) via `FhirContext.forR4Cached().newFhirPath()`:
1. **Compilation at Initialization**: When loading `--rules custom-rules.yaml`, every rule's `fhirpath` string is parsed and validated into a pre-compiled AST expression using `fhirPathEngine.parse(expression)`. Any syntax error aborts immediately with Exit Code 2.
2. **Context-Relative Evaluation**: Rules specify a target `resourceType` (e.g. `Observation`). During resource graph iteration, the rule is evaluated only against resources matching that type. The expression is evaluated in the root context of that resource instance:
   ```java
   boolean valid = fhirPathEngine.evaluateToBoolean(resourceInstance, compiledExpression);
   if (!valid) {
       // emit QualityIssue with ruleId, severity, message, suggestion, and path
   }
   ```
3. **Pluggable Architecture**: Custom rules implement the existing `QualityRule` interface via a generic adapter `FhirPathQualityRule`, integrating seamlessly into the existing multi-category scoring formula and rule registry.

### Rationale
- Native to HAPI FHIR R4, requiring zero additional runtime expression libraries.
- AST pre-compilation ensures sub-millisecond evaluation per resource, easily satisfying NFR-002.
- Fits directly into the existing `QualityRule` dispatch pipeline without altering scoring math.

### Alternatives Considered
- **Spring Expression Language (SpEL) / MVEL**: Rejected because SpEL introduces heavy dependencies and does not understand FHIR models or FHIRPath path traversal.
- **Custom regex or JSONPath**: Rejected because healthcare developers write FHIR invariants in standard HL7 FHIRPath.

---

## 3. YAML Parsing & GraalVM Native Image Reachability

### Context & Problem
Allowing users to pass `--rules custom-rules.yaml` requires YAML deserialization. Under Constitution Principle VI, every dependency must be justified. Furthermore, GraalVM AOT native compilation requires ahead-of-time reflection and resource configuration for dynamic parsers.

### Decision
Add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2` to `build.gradle`:
1. **Version Alignment**: Matches the exact version of `jackson-databind` and `jackson-datatype-jsr310` already in the project.
2. **Minimal Footprint**: Jackson YAML wraps `SnakeYAML`, which is already transitively present or easily linked.
3. **GraalVM Reachability Metadata**:
   - Register `CustomRulesFile` and `CustomRuleDefinition` in `reflect-config.json` with full reflective constructors and getters.
   - Register SnakeYAML parser classes (`org.yaml.snakeyaml.constructor.Constructor`, etc.) in `reflect-config.json`.
   - Add SnakeYAML version resource files to `resource-config.json`.

### Rationale
- Standardized developer experience: developers expect YAML for linter configuration.
- Shared Jackson object mapper semantics (naming strategies, validation, clear line/column error reporting).

---

## 4. Subcommand Architecture & Core/CLI Separation

### Context & Problem
We need:
1. `fhir-lint validate <input> [options]`
2. `fhir-lint compare <baseline> <target> [options]`
3. Programmatic Java access for both operations without invoking CLI processes.

### Decision
1. **Core Library (`org.fhirlint.core.comparison`)**:
   - `DatasetComparator`: Pure Java service with method:
     `ComparisonReport compare(LintReport baseline, LintReport target, ComparisonOptions options)`
   - `ComparisonReport`: Immutable model containing score delta, category deltas, new issues, resolved issues, persistent issues, and resource deltas.
   - `ComparisonGateResult`: Evaluates `--fail-on-regression` and `--max-score-drop`.
2. **CLI Layer (`org.fhirlint.cli.command`)**:
   - `FhirLintApplication`: Root Picocli command registering `validate` and `compare` subcommands.
   - `CompareCommand`: Picocli subcommand parsing `<baseline>` and `<target>`, sequentially running `FhirLinter` on both inputs, calling `DatasetComparator`, and rendering results via `ComparisonTableRenderer` or `ComparisonJsonRenderer`.
   - `CompareExitCodes`: Precedence contract (2 for parameter/file error, 1 for gate violation, 0 for pass).

### Rationale
- Preserves Constitution Principle II & VII (clean decoupling between core engine and CLI presentation).
- Allows CI/CD pipelines and embedded Java applications to perform comparison directly in memory.
