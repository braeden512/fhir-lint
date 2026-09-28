package org.fhirlint.core.rules.terminology;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default offline implementation of TerminologyService backed by static canonical tables.
 */
public class DefaultTerminologyService implements TerminologyService {

    public static final String UCUM_SYSTEM = "http://unitsofmeasure.org";
    public static final String LOINC_SYSTEM = "http://loinc.org";
    public static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    public static final String RXNORM_SYSTEM = "http://hl7.org/fhir/sid/rxnorm";
    public static final String ICD10CM_SYSTEM = "http://hl7.org/fhir/sid/icd-10-cm";

    private static final Set<String> CANONICAL_SYSTEMS;
    private static final Map<String, String> SYSTEM_SUGGESTIONS;
    private static final Set<String> COMMON_UCUM_UNITS;
    private static final Set<String> ADMINISTRATIVE_GENDERS;
    private static final Set<String> ENCOUNTER_STATUSES;
    private static final Set<String> CONDITION_CLINICAL_STATUSES;

    static {
        Set<String> canonical = new HashSet<>();
        canonical.add(LOINC_SYSTEM);
        canonical.add(SNOMED_SYSTEM);
        canonical.add(RXNORM_SYSTEM);
        canonical.add(ICD10CM_SYSTEM);
        canonical.add(UCUM_SYSTEM);
        canonical.add("http://hl7.org/fhir/sid/ndc");
        canonical.add("http://hl7.org/fhir/sid/cvx");
        canonical.add("http://www.ama-assn.org/go/cpt");
        canonical.add("http://hl7.org/fhir/administrative-gender");
        canonical.add("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation");
        canonical.add("http://terminology.hl7.org/CodeSystem/condition-clinical");
        canonical.add("http://terminology.hl7.org/CodeSystem/condition-ver-status");
        canonical.add("http://terminology.hl7.org/CodeSystem/encounter-status");
        CANONICAL_SYSTEMS = Collections.unmodifiableSet(canonical);

        Map<String, String> suggestions = new HashMap<>();
        suggestions.put("http://loinc.org/", LOINC_SYSTEM);
        suggestions.put("https://loinc.org", LOINC_SYSTEM);
        suggestions.put("https://loinc.org/", LOINC_SYSTEM);
        suggestions.put("loinc", LOINC_SYSTEM);
        suggestions.put("LOINC", LOINC_SYSTEM);

        suggestions.put("http://snomed.info", SNOMED_SYSTEM);
        suggestions.put("http://snomed.info/", SNOMED_SYSTEM);
        suggestions.put("https://snomed.info/sct", SNOMED_SYSTEM);
        suggestions.put("snomed", SNOMED_SYSTEM);
        suggestions.put("SNOMED", SNOMED_SYSTEM);
        suggestions.put("http://snomed.info/sct/", SNOMED_SYSTEM);

        suggestions.put("http://www.nlm.nih.gov/research/umls/rxnorm", RXNORM_SYSTEM);
        suggestions.put("https://www.nlm.nih.gov/research/umls/rxnorm", RXNORM_SYSTEM);
        suggestions.put("rxnorm", RXNORM_SYSTEM);
        suggestions.put("RxNorm", RXNORM_SYSTEM);

        suggestions.put("http://hl7.org/fhir/sid/icd-10", ICD10CM_SYSTEM);
        suggestions.put("http://hl7.org/fhir/sid/icd-10/", ICD10CM_SYSTEM);
        suggestions.put("icd-10", ICD10CM_SYSTEM);
        suggestions.put("icd-10-cm", ICD10CM_SYSTEM);
        suggestions.put("ICD-10-CM", ICD10CM_SYSTEM);
        SYSTEM_SUGGESTIONS = Collections.unmodifiableMap(suggestions);

        Set<String> ucum = new HashSet<>();
        // Common clinical & vital signs units
        ucum.add("mm[Hg]");
        ucum.add("kg");
        ucum.add("g");
        ucum.add("mg");
        ucum.add("ug");
        ucum.add("cm");
        ucum.add("m");
        ucum.add("mm");
        ucum.add("[in_i]");
        ucum.add("[lb_av]");
        ucum.add("[oz_av]");
        ucum.add("Cel");
        ucum.add("cel");
        ucum.add("[degF]");
        ucum.add("/min");
        ucum.add("1/min");
        ucum.add("{beats}/min");
        ucum.add("{breaths}/min");
        ucum.add("%");
        ucum.add("g/dL");
        ucum.add("mg/dL");
        ucum.add("mmol/L");
        ucum.add("umol/L");
        ucum.add("kg/m2");
        ucum.add("mL");
        ucum.add("L");
        ucum.add("1");
        ucum.add("{count}");
        COMMON_UCUM_UNITS = Collections.unmodifiableSet(ucum);

        Set<String> genders = new HashSet<>();
        genders.add("male");
        genders.add("female");
        genders.add("other");
        genders.add("unknown");
        ADMINISTRATIVE_GENDERS = Collections.unmodifiableSet(genders);

        Set<String> encounterStatuses = new HashSet<>();
        encounterStatuses.add("planned");
        encounterStatuses.add("arrived");
        encounterStatuses.add("triaged");
        encounterStatuses.add("in-progress");
        encounterStatuses.add("onleave");
        encounterStatuses.add("finished");
        encounterStatuses.add("cancelled");
        encounterStatuses.add("entered-in-error");
        encounterStatuses.add("unknown");
        ENCOUNTER_STATUSES = Collections.unmodifiableSet(encounterStatuses);

        Set<String> clinicalStatuses = new HashSet<>();
        clinicalStatuses.add("active");
        clinicalStatuses.add("recurrence");
        clinicalStatuses.add("relapse");
        clinicalStatuses.add("inactive");
        clinicalStatuses.add("remission");
        clinicalStatuses.add("resolved");
        CONDITION_CLINICAL_STATUSES = Collections.unmodifiableSet(clinicalStatuses);
    }

    /**
     * Checks if the given code system URI is canonical.
     * Operates in alias and misspelling detection mode: known legacy, misspelled, or non-canonical
     * variants (e.g. trailing slashes, pre-R4 URLs) return false, while recognized canonical systems
     * and custom institutional URIs return true.
     */
    @Override
    public boolean isCanonicalSystemUri(String system) {
        if (system == null || system.isBlank()) {
            return false;
        }
        if (SYSTEM_SUGGESTIONS.containsKey(system)) {
            return false;
        }
        return true;
    }

    @Override
    public Set<String> getCanonicalSystems() {
        return CANONICAL_SYSTEMS;
    }

    @Override
    public Optional<String> getCanonicalSuggestion(String system) {
        if (system == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SYSTEM_SUGGESTIONS.get(system));
    }

    @Override
    public boolean isValidUcumUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        return COMMON_UCUM_UNITS.contains(unit.trim());
    }

    @Override
    public boolean isValidGender(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return ADMINISTRATIVE_GENDERS.contains(code.trim().toLowerCase());
    }

    @Override
    public boolean isValidEncounterStatus(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return ENCOUNTER_STATUSES.contains(code.trim().toLowerCase());
    }

    @Override
    public boolean isValidConditionClinicalStatus(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return CONDITION_CLINICAL_STATUSES.contains(code.trim().toLowerCase());
    }
}
