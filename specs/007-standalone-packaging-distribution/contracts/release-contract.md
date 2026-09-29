# Release Manifest & Checksum Contract: `.github/workflows/release.yml`

**Contract Name**: Multi-Platform Release Distribution Contract
**Reference**: [spec.md](../spec.md)

---

## 1. Distribution Artifact Naming & Runner Matrix

All release binaries and packages adhere to standardized naming conventions:

| Artifact Name | Platform Target | Runner Environment | Output Format |
| :--- | :--- | :--- | :--- |
| `fhir-lint-linux-x86_64` | Linux (Intel/AMD 64-bit) | `ubuntu-latest` | Standalone ELF 64-bit binary |
| `fhir-lint-macos-aarch64` | macOS (Apple Silicon M1/M2/M3/M4) | `macos-14` | Standalone Mach-O arm64 binary |
| `fhir-lint-macos-x86_64` | macOS (Intel 64-bit) | `macos-13` | Standalone Mach-O x86_64 binary |
| `fhir-lint-windows-x86_64.exe`| Windows (x86_64) | `windows-latest` | Standalone PE32+ executable |
| `fhir-lint-all.jar` | Universal JVM (Java 21+) | `ubuntu-latest` | Executable Fat JAR |

---

## 2. Checksum Verification Contract

Every released binary is published alongside a matching `.sha256` checksum file containing standard GNU coreutils checksum format:

```text
<64-hex-sha256-hash>  <artifact-name>
```

### Verification Command
```bash
sha256sum -c fhir-lint-linux-x86_64.sha256
```
Expected output:
```text
fhir-lint-linux-x86_64: OK
```

---

## 3. GitHub Release Workflow Trigger Contract

- **Trigger**: Tag push matching `v*.*.*` (e.g. `v0.1.0`).
- **Pre-publish Validation**: Each runner executes `./<binary> --version` and `./<binary> validate sample-data/clean/clean-bundle.json` to verify runtime health before uploading.
- **Publish Action**: `softprops/action-gh-release@v2` attaches all binaries, JARs, and `.sha256` digests to the GitHub Release.
