package com.braeden.fhirlint.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;
import com.braeden.fhirlint.core.model.IssueCategory;
import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.Severity;
import com.braeden.fhirlint.core.model.ValidationProfile;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Core validation engine wrapping HAPI FHIR validator and cached support chains.
 * Complies with ADR-002: Base R4 schema checks, US Core v3.1.1 profile conformance,
 * thread-safe caching, and message normalization.
 */
public class FhirValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(FhirValidationEngine.class);

    // US Core 3.1.1 Profile URLs (vital signs uses HL7 FHIR R4 vitalsigns base profile per US Core v3.1.1)
    public static final String US_CORE_PATIENT = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient";
    public static final String US_CORE_ENCOUNTER = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter";
    public static final String US_CORE_CONDITION = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition";
    public static final String US_CORE_VITAL_SIGNS = "http://hl7.org/fhir/StructureDefinition/vitalsigns";
    public static final String US_CORE_OBS_LAB = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab";
    public static final String US_CORE_MED_REQ = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-medicationrequest";
    public static final String US_CORE_DIAG_REPORT_NOTE = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-note";
    public static final String US_CORE_DIAG_REPORT_LAB = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-lab";

    private final FhirContext fhirContext;
    private final ValidationSupportFactory supportFactory;
    private final ValidationMessageNormalizer normalizer;
    private final ConcurrentMap<ValidationProfile, FhirValidator> validatorCache = new ConcurrentHashMap<>();

    public FhirValidationEngine() {
        this(FhirContext.forR4Cached(), new ValidationSupportFactory(), new ValidationMessageNormalizer());
    }

    public FhirValidationEngine(FhirContext fhirContext, ValidationSupportFactory supportFactory, ValidationMessageNormalizer normalizer) {
        this.fhirContext = fhirContext;
        this.supportFactory = supportFactory;
        this.normalizer = normalizer;
    }

    public ValidationMessageNormalizer getNormalizer() {
        return normalizer;
    }

    public List<QualityIssue> validateResource(IBaseResource resource, ValidationProfile profile) {
        if (resource == null) {
            return List.of();
        }

        FhirValidator validator = getOrCreateValidator(profile);
        ValidationOptions options = new ValidationOptions();

        String resourceType = resource.fhirType();
        String resourceId = null;
        if (resource instanceof Resource r && r.hasIdElement()) {
            resourceId = r.getIdElement().getIdPart();
        }

        if (profile == ValidationProfile.US_CORE) {
            String defaultProfile = resolveUsCoreProfile(resource);
            if (defaultProfile != null) {
                options.addProfile(defaultProfile);
            }
        }

        ValidationResult result = validator.validateWithResult(resource, options);
        return normalizer.normalize(result.getMessages(), resourceType, resourceId);
    }

    public List<QualityIssue> validateAll(List<? extends IBaseResource> resources, ValidationProfile profile) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }

        List<QualityIssue> allIssues = new ArrayList<>();
        for (IBaseResource resource : resources) {
            allIssues.addAll(validateResource(resource, profile));
        }
        return allIssues;
    }

    private FhirValidator getOrCreateValidator(ValidationProfile profile) {
        return validatorCache.computeIfAbsent(profile, p -> {
            IValidationSupport support = supportFactory.getValidationSupport(p);
            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(support);
            instanceValidator.setAnyExtensionsAllowed(true);
            instanceValidator.setErrorForUnknownProfiles(false);
            instanceValidator.setNoTerminologyChecks(false);

            FhirValidator validator = fhirContext.newValidator();
            validator.registerValidatorModule(instanceValidator);
            return validator;
        });
    }

    public String resolveUsCoreProfile(IBaseResource resource) {
        if (resource instanceof Resource r && r.hasMeta() && !r.getMeta().getProfile().isEmpty()) {
            // Already has profile declared
            return null;
        }

        String type = resource.fhirType();
        return switch (type) {
            case "Patient" -> US_CORE_PATIENT;
            case "Encounter" -> US_CORE_ENCOUNTER;
            case "Condition" -> US_CORE_CONDITION;
            case "MedicationRequest" -> US_CORE_MED_REQ;
            case "Observation" -> resolveObservationProfile((Observation) resource);
            case "DiagnosticReport" -> resolveDiagnosticReportProfile((DiagnosticReport) resource);
            default -> null;
        };
    }

    private String resolveObservationProfile(Observation observation) {
        if (observation.hasCategory()) {
            for (CodeableConcept cc : observation.getCategory()) {
                for (Coding c : cc.getCoding()) {
                    if ("vital-signs".equalsIgnoreCase(c.getCode())) {
                        return US_CORE_VITAL_SIGNS;
                    }
                    if ("laboratory".equalsIgnoreCase(c.getCode())) {
                        return US_CORE_OBS_LAB;
                    }
                }
            }
        }
        return null;
    }

    private String resolveDiagnosticReportProfile(DiagnosticReport report) {
        if (report.hasCategory()) {
            for (CodeableConcept cc : report.getCategory()) {
                for (Coding c : cc.getCoding()) {
                    if ("laboratory".equalsIgnoreCase(c.getCode()) || "LAB".equalsIgnoreCase(c.getCode())) {
                        return US_CORE_DIAG_REPORT_LAB;
                    }
                }
            }
        }
        return US_CORE_DIAG_REPORT_NOTE;
    }
}
