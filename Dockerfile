# Multi-stage container build for FHIRLint
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY . .
RUN if [ -f build/native/nativeCompile/fhir-lint ]; then \
      cp build/native/nativeCompile/fhir-lint /build/fhir-lint; \
    else \
      ./gradlew nativeCompile --no-daemon && cp build/native/nativeCompile/fhir-lint /build/fhir-lint; \
    fi

# Minimal distroless runtime with glibc support
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /workspace
COPY --from=builder /build/fhir-lint /fhir-lint
USER 65532:65532
ENTRYPOINT ["/fhir-lint"]
CMD ["--help"]
