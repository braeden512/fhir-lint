package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.rules.terminology.DefaultTerminologyService;
import org.fhirlint.core.rules.terminology.TerminologyService;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of QualityRuleEngine orchestrating dataset and resource rule execution.
 */
public class DefaultQualityRuleEngine implements QualityRuleEngine {

    private final RuleRegistry registry;
    private final TerminologyService terminologyService;

    public DefaultQualityRuleEngine(RuleRegistry registry, TerminologyService terminologyService) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.terminologyService = Objects.requireNonNull(terminologyService, "terminologyService must not be null");
    }

    public DefaultQualityRuleEngine(RuleRegistry registry) {
        this(registry, new DefaultTerminologyService());
    }

    public DefaultQualityRuleEngine() {
        this(RuleRegistry.createDefault(), new DefaultTerminologyService());
    }

    @Override
    public List<QualityIssue> evaluate(List<? extends IBaseResource> dataset, ResourceGraphIndex graphIndex) {
        if (dataset == null || dataset.isEmpty()) {
            return Collections.emptyList();
        }

        List<QualityIssue> issues = new ArrayList<>();

        // 1. Evaluate Dataset-scoped rules (e.g. duplicate patient detection)
        List<QualityRule> datasetRules = registry.getDatasetRules();
        if (!datasetRules.isEmpty()) {
            RuleContext datasetContext = new RuleContext(null, dataset, graphIndex, terminologyService);
            for (QualityRule rule : datasetRules) {
                issues.addAll(rule.evaluate(datasetContext));
            }
        }

        // 2. Evaluate Resource-scoped rules per matching resource
        for (IBaseResource resource : dataset) {
            String fhirType = resource.fhirType();
            List<QualityRule> rules = registry.getRulesForType(fhirType);
            if (!rules.isEmpty()) {
                RuleContext resourceContext = new RuleContext(resource, dataset, graphIndex, terminologyService);
                for (QualityRule rule : rules) {
                    issues.addAll(rule.evaluate(resourceContext));
                }
            }
        }

        return issues;
    }

    public RuleRegistry getRegistry() {
        return registry;
    }

    public TerminologyService getTerminologyService() {
        return terminologyService;
    }
}
