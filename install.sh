#!/usr/bin/env bash
set -euo pipefail

REPO="braeden512/fhir-lint"
GITHUB_URL="https://github.com/${REPO}/releases/latest/download"

# Determine OS and Architecture
OS="$(uname -s)"
ARCH="$(uname -m)"

# Choose install directory
if [ "$(id -u)" -eq 0 ]; then
    BIN_DIR="/usr/local/bin"
    DATA_DIR="/usr/local/share/fhir-lint"
else
    BIN_DIR="${HOME}/.local/bin"
    DATA_DIR="${HOME}/.local/share/fhir-lint"
fi

mkdir -p "${BIN_DIR}"

echo "Installing FHIRLint for ${OS} (${ARCH})..."

if [ "${OS}" = "Linux" ] && { [ "${ARCH}" = "x86_64" ] || [ "${ARCH}" = "amd64" ]; }; then
    echo "Downloading native AOT binary (Linux x86_64)..."
    curl -fsSL -o "${BIN_DIR}/fhir-lint" "${GITHUB_URL}/fhir-lint-linux-x86_64"
    chmod +x "${BIN_DIR}/fhir-lint"
else
    echo "Downloading universal package..."
    mkdir -p "${DATA_DIR}"
    curl -fsSL -o "${DATA_DIR}/fhir-lint-all.jar" "${GITHUB_URL}/fhir-lint-all.jar"
    
    cat << EOF > "${BIN_DIR}/fhir-lint"
#!/usr/bin/env bash
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java 21+ is required to run FHIRLint on this platform." >&2
    echo "Please install OpenJDK 21 or run on a supported native platform (Linux x86_64, Windows x86_64)." >&2
    exit 1
fi
JAR_PATH="\${FHIR_LINT_JAR:-${DATA_DIR}/fhir-lint-all.jar}"
exec java -jar "\${JAR_PATH}" "\$@"
EOF
    chmod +x "${BIN_DIR}/fhir-lint"
fi

echo ""
echo "FHIRLint installed successfully to ${BIN_DIR}/fhir-lint!"

if [[ ":$PATH:" != *":${BIN_DIR}:"* ]]; then
    echo "Note: ${BIN_DIR} is not in your PATH."
    echo "Add it by adding this to your shell profile (.bashrc / .zshrc):"
    echo "  export PATH=\"${BIN_DIR}:\$PATH\""
fi

if command -v "${BIN_DIR}/fhir-lint" >/dev/null 2>&1; then
    "${BIN_DIR}/fhir-lint" --version || true
elif [ -x "${BIN_DIR}/fhir-lint" ]; then
    "${BIN_DIR}/fhir-lint" --version || true
fi
