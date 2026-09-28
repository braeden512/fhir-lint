package org.fhirlint.core.rules;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRegistryTest {

    private RuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RuleRegistry();
    }

    @Test
    void shouldRegisterAndIndexRulesByScopeAndType() {
        QualityRule encounterRule = new MockRule("TEST-001", RuleScope.RESOURCE, Set.of("Encounter"));
        QualityRule universalRule = new MockRule("TEST-002", RuleScope.RESOURCE, Collections.emptySet());
        QualityRule datasetRule = new MockRule("TEST-003", RuleScope.DATASET, Set.of("Patient"));

        registry.register(encounterRule);
        registry.register(universalRule);
        registry.register(datasetRule);

        assertThat(registry.getRules()).hasSize(3);
        assertThat(registry.getDatasetRules()).containsExactly(datasetRule);

        // Encounter matches encounterRule + universalRule
        assertThat(registry.getRulesForType("Encounter")).containsExactlyInAnyOrder(encounterRule, universalRule);

        // Patient matches universalRule (not datasetRule which is DATASET scope)
        assertThat(registry.getRulesForType("Patient")).containsExactly(universalRule);

        // Unknown resource type matches universalRule
        assertThat(registry.getRulesForType("Observation")).containsExactly(universalRule);
    }

    @Test
    void shouldCreateDefaultRegistryWith11CatalogRules() {
        RuleRegistry defaultRegistry = RuleRegistry.createDefault();
        assertThat(defaultRegistry.getRules()).hasSize(11);
        assertThat(defaultRegistry.getDatasetRules()).hasSize(2); // DUP-001 and DUP-002
        assertThat(defaultRegistry.getRules())
                .extracting(QualityRule::getRuleId)
                .containsExactlyInAnyOrder(
                        "CONS-001", "CONS-002", "CONS-003", "CONS-004",
                        "DUP-001", "DUP-002",
                        "TERM-001", "TERM-002", "TERM-003",
                        "COMP-001", "COMP-002"
                );
    }

    private static class MockRule implements QualityRule {
        private final String id;
        private final RuleScope scope;
        private final Set<String> types;

        MockRule(String id, RuleScope scope, Set<String> types) {
            this.id = id;
            this.scope = scope;
            this.types = types;
        }

        @Override public String getRuleId() { return id; }
        @Override public String getName() { return id; }
        @Override public IssueCategory getCategory() { return IssueCategory.CONSISTENCY; }
        @Override public Severity getDefaultSeverity() { return Severity.ERROR; }
        @Override public RuleScope getScope() { return scope; }
        @Override public Set<String> getApplicableResourceTypes() { return types; }
        @Override public List<QualityIssue> evaluate(RuleContext context) { return Collections.emptyList(); }
    }
}
