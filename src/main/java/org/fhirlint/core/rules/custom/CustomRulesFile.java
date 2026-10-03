package org.fhirlint.core.rules.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Root container for custom rule definitions parsed from YAML.
 */
public record CustomRulesFile(
    List<CustomRuleDefinition> rules
) {
    @JsonCreator
    public CustomRulesFile(
        @JsonProperty("rules") List<CustomRuleDefinition> rules
    ) {
        this.rules = rules == null ? Collections.emptyList() : Collections.unmodifiableList(rules);
    }
}
