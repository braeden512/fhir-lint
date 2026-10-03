package org.fhirlint.core.rules.custom;

import ca.uhn.fhir.fhirpath.IFhirPath;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.QualityRule;
import org.fhirlint.core.rules.RuleContext;
import org.fhirlint.core.rules.RuleScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;

import java.util.*;

/**
 * QualityRule adapter executing a dynamic FHIRPath invariant expression against candidate resources.
 */
public class FhirPathQualityRule implements QualityRule {

    private final CustomRuleDefinition definition;
    private final IFhirPath.IParsedExpression parsedExpression;
    private final IFhirPath fhirPath;

    public FhirPathQualityRule(CustomRuleDefinition definition, IFhirPath.IParsedExpression parsedExpression, IFhirPath fhirPath) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.parsedExpression = Objects.requireNonNull(parsedExpression, "parsedExpression must not be null");
        this.fhirPath = Objects.requireNonNull(fhirPath, "fhirPath must not be null");
    }

    public CustomRuleDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getRuleId() {
        return definition.id();
    }

    @Override
    public String getName() {
        return definition.name();
    }

    @Override
    public IssueCategory getCategory() {
        return definition.category();
    }

    @Override
    public Severity getDefaultSeverity() {
        return definition.severity();
    }

    @Override
    public RuleScope getScope() {
        return RuleScope.RESOURCE;
    }

    @Override
    public Set<String> getApplicableResourceTypes() {
        if (definition.resourceType() != null && !definition.resourceType().isBlank()) {
            return Set.of(definition.resourceType());
        }
        return Collections.emptySet();
    }

    @Override
    public List<QualityIssue> evaluate(RuleContext context) {
        Optional<IBaseResource> optRes = context.getResource();
        if (optRes.isEmpty()) {
            return Collections.emptyList();
        }

        IBaseResource resource = optRes.get();
        if (definition.resourceType() != null && !resource.fhirType().equalsIgnoreCase(definition.resourceType())) {
            return Collections.emptyList();
        }

        boolean valid = false;
        try {
            Optional<BooleanType> result = fhirPath.evaluateFirst(resource, parsedExpression, BooleanType.class);
            if (result.isPresent() && Boolean.TRUE.equals(result.get().getValue())) {
                valid = true;
            }
        } catch (Exception e) {
            valid = false;
        }

        if (!valid) {
            String resId = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
            String path = resource.fhirType() + (resId != null ? "/" + resId : "");
            QualityIssue issue = QualityIssue.builder()
                    .ruleId(definition.id())
                    .severity(definition.severity())
                    .category(definition.category())
                    .resourceType(resource.fhirType())
                    .resourceId(resId)
                    .path(path)
                    .message(definition.message())
                    .suggestion(definition.suggestion())
                    .build();
            return List.of(issue);
        }

        return Collections.emptyList();
    }
}
