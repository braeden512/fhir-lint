package org.fhirlint.core.rules.terminology;

import java.util.Optional;

/**
 * Service providing offline healthcare terminology and canonical code system verification.
 */
public interface TerminologyService {

    /**
     * Checks if the given code system URI is canonical.
     *
     * @param system code system URI string
     * @return true if canonical or recognized as valid
     */
    boolean isCanonicalSystemUri(String system);

    /**
     * Returns the recommended canonical URI if the given system is a known non-canonical,
     * misspelled, or legacy variant.
     *
     * @param system non-canonical code system URI
     * @return Optional containing canonical suggestion if known, empty otherwise
     */
    Optional<String> getCanonicalSuggestion(String system);

    /**
     * Verifies if the given unit string is a recognized UCUM unit code.
     *
     * @param unit UCUM unit string
     * @return true if valid recognized UCUM unit
     */
    boolean isValidUcumUnit(String unit);

    /**
     * Validates if the given code is a permitted administrative gender.
     *
     * @param code gender code
     * @return true if code is male, female, other, or unknown
     */
    boolean isValidGender(String code);

    /**
     * Validates if the given code is a permitted EncounterStatus.
     *
     * @param code encounter status code
     * @return true if valid FHIR R4 encounter status
     */
    boolean isValidEncounterStatus(String code);

    /**
     * Validates if the given code is a permitted ConditionClinicalStatusCodes.
     *
     * @param code condition clinical status code
     * @return true if valid FHIR R4 condition clinical status
     */
    boolean isValidConditionClinicalStatus(String code);

    /**
     * Returns the immutable set of standard canonical code system URIs recognized by FHIR R4 and US Core.
     *
     * @return set of canonical system URIs
     */
    java.util.Set<String> getCanonicalSystems();
}
