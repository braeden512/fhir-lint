# OCI Container Runner Contract: `Dockerfile`

**Contract Name**: FHIRLint Container Runner Contract
**Reference**: [spec.md](../spec.md)

---

## 1. Container Specification

| Attribute | Contract Value |
| :--- | :--- |
| **Image Base** | `gcr.io/distroless/cc-debian12:nonroot` |
| **Image Name** | `ghcr.io/braeden512/fhir-lint` (or `fhir-lint:latest`) |
| **User Context** | Non-root `UID: 65532, GID: 65532` (`nonroot:nonroot`) |
| **Working Directory** | `/workspace` |
| **Default Entrypoint** | `["/fhir-lint"]` |
| **Default Command** | `["--help"]` |
| **Exposed Ports** | None (Stateless CLI tool) |

---

## 2. Invocation Patterns

### Pattern A: Mounted Local File / Directory Analysis
```bash
docker run --rm \
  -v $(pwd)/sample-data:/workspace \
  ghcr.io/braeden512/fhir-lint \
  validate /workspace/clean/clean-bundle.json --format table
```

### Pattern B: Stdin Stream Ingestion (Pipe)
```bash
cat bundle.json | docker run -i --rm \
  ghcr.io/braeden512/fhir-lint \
  validate - --format json
```

### Pattern C: CI/CD Pipeline Stage (GitLab CI / Tekton / Argo)
```yaml
# Example GitLab CI step
fhir_lint_gate:
  image: ghcr.io/braeden512/fhir-lint:latest
  script:
    - /fhir-lint validate ./fhir-fixtures/ --min-score 80 --fail-on error
```

---

## 3. Exit Code Contract
The container exits with the exact POSIX code emitted by the native binary:
- `0`: Quality check passed.
- `1`: Quality gate breach.
- `2`: Invocation / file syntax error.
