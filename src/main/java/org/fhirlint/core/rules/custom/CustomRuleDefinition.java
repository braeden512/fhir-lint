package org.fhirlint.core.rules.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.Severity;

import java.util.Objects;

/**
 * POJO/Record representing an individual custom rule loaded from a YAML specification.
 */
public record CustomRuleDefinition(
    String id,
    String name,
    String description,
    String resourceType,
    IssueCategory category,
    Severity severity,
    String fhirpath,
    String message,
    String suggestion
) {
    @JsonCreator
    public CustomRuleDefinition(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty("description") String description,
        @JsonProperty("resourceType") String resourceType,
        @JsonProperty(value = "category", required = true) String category,
        @JsonProperty(value = "severity", required = true) String severity,
        @JsonProperty(value = "fhirpath", required = true) String fhirpath,
        @JsonProperty(value = "message", required = true) String message,
        @JsonProperty("suggestion") String suggestion
    ) {
        this(
            validateId(id),
            Objects.requireNonNull(name, "rule 'name' must not be null"),
            description,
            resourceType != null && !resourceType.isBlank() ? resourceType.trim() : null,
            parseCategory(category),
            parseSeverity(severity),
            Objects.requireNonNull(fhirpath, "rule 'fhirpath' must not be null"),
            Objects.requireNonNull(message, "rule 'message' must not be null"),
            suggestion
        );
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "rule 'id' must not be null");
        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Rule id must match ^[a-zA-Z0-9_-]+$, was: " + id);
        }
        return id;
    }

    private static IssueCategory parseCategory(String cat) {
        Objects.requireNonNull(cat, "rule 'category' must not be null");
        String normalized = cat.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        // Handle common camelCase variants (e.g. profileConformance -> PROFILE_CONFORMANCE)
        normalized = normalized.replaceAll("(?<!^)(?=[A-Z])", "_").toUpperCase();
        for (IssueCategory c : IssueCategory.values()) {
            if (c.name().equalsIgnoreCase(normalized) || c.name().replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown quality category: '" + cat + "'");
    }

    private static Severity parseSeverity(String sev) {
        Objects.requireNonNull(sev, "rule 'severity' must not be null");
        try {
            return Severity.valueOf(sev.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown severity: '" + sev + "'. Allowed: error, warning, info");
        }
    }
}
