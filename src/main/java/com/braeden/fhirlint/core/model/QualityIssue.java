package com.braeden.fhirlint.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An individual finding produced during data quality analysis.
 */
public record QualityIssue(
    String id,
    Severity severity,
    IssueCategory category,
    String ruleId,
    String resourceType,
    String resourceId,
    String path,
    String message,
    String suggestion
) {
    public QualityIssue {
        if (id == null) {
            id = "issue_" + UUID.randomUUID().toString().substring(0, 8);
        }
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Severity severity;
        private IssueCategory category;
        private String ruleId;
        private String resourceType;
        private String resourceId;
        private String path;
        private String message;
        private String suggestion;

        public Builder id(String id) { this.id = id; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder category(IssueCategory category) { this.category = category; return this; }
        public Builder ruleId(String ruleId) { this.ruleId = ruleId; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder suggestion(String suggestion) { this.suggestion = suggestion; return this; }

        public QualityIssue build() {
            return new QualityIssue(id, severity, category, ruleId, resourceType, resourceId, path, message, suggestion);
        }
    }
}
