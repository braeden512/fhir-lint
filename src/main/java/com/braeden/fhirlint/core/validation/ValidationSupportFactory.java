package com.braeden.fhirlint.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import com.braeden.fhirlint.core.model.ValidationProfile;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory for creating and caching thread-safe HAPI FHIR ValidationSupportChains.
 * Complies with ADR-002: Base R4 support, preloaded US Core NPM package, snapshot generation,
 * in-memory terminology, and LRU caching support.
 */
public class ValidationSupportFactory {

    private static final Logger log = LoggerFactory.getLogger(ValidationSupportFactory.class);
    private static final String US_CORE_PACKAGE_PATH = "profiles/us-core/package.tgz";

    private final FhirContext fhirContext;
    private final ConcurrentMap<ValidationProfile, IValidationSupport> supportCache = new ConcurrentHashMap<>();

    public ValidationSupportFactory() {
        this(FhirContext.forR4Cached());
    }

    public ValidationSupportFactory(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public IValidationSupport getValidationSupport(ValidationProfile profile) {
        return supportCache.computeIfAbsent(profile, this::buildValidationSupport);
    }

    private IValidationSupport buildValidationSupport(ValidationProfile profile) {
        log.debug("Building validation support chain for profile: {}", profile);

        DefaultProfileValidationSupport defaultSupport = new DefaultProfileValidationSupport(fhirContext);
        InMemoryTerminologyServerValidationSupport termSupport = new InMemoryTerminologyServerValidationSupport(fhirContext);
        SnapshotGeneratingValidationSupport snapshotSupport = new SnapshotGeneratingValidationSupport(fhirContext);

        ValidationSupportChain chain;
        if (profile == ValidationProfile.US_CORE) {
            NpmPackageValidationSupport npmSupport = new NpmPackageValidationSupport(fhirContext);
            try {
                npmSupport.loadPackageFromClasspath(US_CORE_PACKAGE_PATH);
                log.info("Loaded US Core definitions from classpath: {}", US_CORE_PACKAGE_PATH);
            } catch (IOException e) {
                log.error("Failed to load US Core package from classpath: {}", US_CORE_PACKAGE_PATH, e);
                throw new IllegalStateException("Failed to load embedded US Core package: " + e.getMessage(), e);
            }
            chain = new ValidationSupportChain(
                    defaultSupport,
                    npmSupport,
                    snapshotSupport,
                    termSupport
            );
        } else {
            chain = new ValidationSupportChain(
                    defaultSupport,
                    snapshotSupport,
                    termSupport
            );
        }

        return new CachingValidationSupport(chain);
    }
}
