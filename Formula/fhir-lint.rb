class FhirLint < Formula
  desc "Developer-focused data quality linter for FHIR healthcare data"
  homepage "https://github.com/braeden512/fhir-lint"
  version "0.1.0"
  license "Apache-2.0"

  on_macos do
    url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-all.jar"
    sha256 "PLACEHOLDER_FAT_JAR_SHA256"

    depends_on "openjdk@21"
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-linux-x86_64"
      sha256 "PLACEHOLDER_LINUX_X86_SHA256"
    else
      url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-all.jar"
      sha256 "PLACEHOLDER_FAT_JAR_SHA256"

      depends_on "openjdk@21"
    end
  end

  def install
    if File.exist?("fhir-lint-all.jar")
      libexec.install "fhir-lint-all.jar"
      bin.write_jar_script libexec/"fhir-lint-all.jar", "fhir-lint"
    else
      binary_name = Dir["fhir-lint*"].first
      bin.install binary_name => "fhir-lint"
    end
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/fhir-lint --version")
  end
end
