class FhirLint < Formula
  desc "Developer-focused data quality linter for FHIR healthcare data"
  homepage "https://github.com/braeden512/fhir-lint"
  version "0.1.0"
  license "Apache-2.0"

  on_macos do
    url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-all.jar"
    sha256 "6c68bb9eb8b67bec041cd598553fb01fe6d675a52e0976c6de5212cd0cdd8129"

    depends_on "openjdk@21"
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-linux-x86_64"
      sha256 "0633f71f9762676d1fdccff54f41432b829f60faa2c38745177e1f16a5e2e9a5"
    else
      url "https://github.com/braeden512/fhir-lint/releases/download/v#{version}/fhir-lint-all.jar"
      sha256 "6c68bb9eb8b67bec041cd598553fb01fe6d675a52e0976c6de5212cd0cdd8129"

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
