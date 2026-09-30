class FhirLint < Formula
  desc "Developer-focused data quality linter for FHIR healthcare data"
  homepage "https://github.com/braeden512/fhir-lint"
  version "0.1.0"
  license "Apache-2.0"

  on_macos do
    url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-macos-aarch64"
    sha256 "PLACEHOLDER_MAC_ARM64_SHA256"
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-linux-x86_64"
      sha256 "PLACEHOLDER_LINUX_X86_SHA256"
    end
  end

  def install
    binary_name = Dir["fhir-lint*"].first
    bin.install binary_name => "fhir-lint"
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/fhir-lint --version")
  end
end
