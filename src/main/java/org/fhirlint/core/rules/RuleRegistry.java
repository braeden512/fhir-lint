package org.fhirlint.core.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry holding registered QualityRule implementations with indexed lookups by scope and type.
 */
public class RuleRegistry {

    private final List<QualityRule> allRules = new ArrayList<>();
    private final List<QualityRule> datasetRules = new ArrayList<>();
    private final Map<String, List<QualityRule>> resourceRulesByType = new HashMap<>();
    private final List<QualityRule> universalResourceRules = new ArrayList<>();
    private final Map<String, List<QualityRule>> rulesByTypeCache = new HashMap<>();

    public synchronized RuleRegistry register(QualityRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        allRules.add(rule);
        rulesByTypeCache.clear();

        if (rule.getScope() == RuleScope.DATASET) {
            datasetRules.add(rule);
        } else {
            Set<String> types = rule.getApplicableResourceTypes();
            if (types == null || types.isEmpty() || types.contains("*")) {
                universalResourceRules.add(rule);
            } else {
                for (String type : types) {
                    resourceRulesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(rule);
                }
            }
        }
        return this;
    }

    public synchronized List<QualityRule> getRules() {
        return Collections.unmodifiableList(new ArrayList<>(allRules));
    }

    public synchronized List<QualityRule> getDatasetRules() {
        return Collections.unmodifiableList(new ArrayList<>(datasetRules));
    }

    public synchronized List<QualityRule> getRulesForType(String resourceType) {
        String key = resourceType != null ? resourceType : "*";
        List<QualityRule> cached = rulesByTypeCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<QualityRule> result = new ArrayList<>(universalResourceRules);
        if (resourceType != null) {
            List<QualityRule> specific = resourceRulesByType.get(resourceType);
            if (specific != null) {
                result.addAll(specific);
            }
        }
        List<QualityRule> immutableResult = Collections.unmodifiableList(result);
        rulesByTypeCache.put(key, immutableResult);
        return immutableResult;
    }

    /**
     * Creates and populates a RuleRegistry with the default Phase 4 catalog rules.
     */
    public static RuleRegistry createDefault() {
        RuleRegistry registry = new RuleRegistry();
        // Consistency rules
        registry.register(new org.fhirlint.core.rules.consistency.PeriodChronologyRule());
        registry.register(new org.fhirlint.core.rules.consistency.BirthToEventChronologyRule());
        registry.register(new org.fhirlint.core.rules.consistency.DeceasedStatusRule());
        registry.register(new org.fhirlint.core.rules.consistency.DiagnosticReportObservationStateRule());

        // Duplicate rules
        registry.register(new org.fhirlint.core.rules.duplicate.PatientIdentifierDuplicateRule());
        registry.register(new org.fhirlint.core.rules.duplicate.PatientDemographicDuplicateRule());

        // Terminology rules
        registry.register(new org.fhirlint.core.rules.terminology.CanonicalSystemUriRule());
        registry.register(new org.fhirlint.core.rules.terminology.VitalSignsUcumUnitRule());
        registry.register(new org.fhirlint.core.rules.terminology.CoreValueSetBindingRule());

        // Completeness rules
        registry.register(new org.fhirlint.core.rules.completeness.MissingSubjectContextRule());
        registry.register(new org.fhirlint.core.rules.completeness.MissingObservationValueRule());

        return registry;
    }
}
