package org.fhirlint.core.validation;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes raw HAPI FHIR SingleValidationMessages into developer-friendly QualityIssue records.
 * Complies with ADR-002: Severity mapping, structural vs profile category separation,
 * compiler prose cleanup, and deterministic rule ID generation.
 */
public class ValidationMessageNormalizer {

    private static final Pattern HAPI_CODE_PREFIX = Pattern.compile("^HAPI-\\d+:\\s*");
    private static final Pattern RESOURCE_TYPE_EXTRACTOR = Pattern.compile("(?:^|/|\\.)([A-Z][a-zA-Z0-9]+)(?:\\.|/|\\[|$)");

    public List<QualityIssue> normalize(List<SingleValidationMessage> messages, String fallbackResourceType, String fallbackResourceId) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<QualityIssue> issues = new ArrayList<>(messages.size());
        for (SingleValidationMessage msg : messages) {
            QualityIssue issue = normalizeSingle(msg, fallbackResourceType, fallbackResourceId);
            if (issue != null) {
                issues.add(issue);
            }
        }
        return issues;
    }

    public QualityIssue normalizeSingle(SingleValidationMessage msg, String fallbackResourceType, String fallbackResourceId) {
        if (msg == null) {
            return null;
        }

        Severity severity = mapSeverity(msg.getSeverity());
        String rawMessage = msg.getMessage() != null ? msg.getMessage() : "";
        String cleanedMessage = cleanMessage(rawMessage);
        String path = msg.getLocationString();

        IssueCategory category = determineCategory(rawMessage, path);
        String ruleId = determineRuleId(rawMessage, category);
        String suggestion = generateSuggestion(ruleId, path, cleanedMessage);

        String resourceType = extractResourceType(path, fallbackResourceType);
        String resourceId = fallbackResourceId;

        return QualityIssue.builder()
                .severity(severity)
                .category(category)
                .ruleId(ruleId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .path(path)
                .message(cleanedMessage)
                .suggestion(suggestion)
                .build();
    }

    public Severity mapSeverity(ResultSeverityEnum hapiSeverity) {
        if (hapiSeverity == null) {
            return Severity.WARNING;
        }
        return switch (hapiSeverity) {
            case FATAL, ERROR -> Severity.ERROR;
            case WARNING -> Severity.WARNING;
            case INFORMATION -> Severity.INFO;
        };
    }

    public IssueCategory determineCategory(String message, String path) {
        String combined = ((message != null ? message : "") + " " + (path != null ? path : "")).toLowerCase();

        if (combined.contains("us-core") || combined.contains("hl7.org/fhir/us/core")
                || combined.contains("structuredefinition/vitalsigns") || combined.contains("vscat")
                || combined.contains("slice") || combined.contains("slicing")
                || combined.contains("must support") || combined.contains("invariant")
                || combined.contains("rule ") || combined.contains("vs-")) {
            return IssueCategory.PROFILE_CONFORMANCE;
        }

        return IssueCategory.STRUCTURAL;
    }

    public String determineRuleId(String message, IssueCategory category) {
        String lower = message != null ? message.toLowerCase() : "";

        if (category == IssueCategory.STRUCTURAL) {
            if (lower.contains("regex") || lower.contains("date") || lower.contains("primitive")
                    || lower.contains("not match") || lower.contains("invalid format")) {
                return "STRUCT_PRIMITIVE_FORMAT";
            }
            if (lower.contains("unknown element") || lower.contains("unrecognized") || lower.contains("not defined")) {
                return "STRUCT_UNKNOWN_ELEMENT";
            }
            if (lower.contains("minimum allowed") || lower.contains("maximum allowed")
                    || lower.contains("cardinality") || lower.contains("at least") || lower.contains("missing required")) {
                return "STRUCT_CARDINALITY";
            }
            return "STRUCT_DATATYPE";
        } else {
            if (lower.contains("slice") || lower.contains("slicing") || lower.contains("discriminator")) {
                return "USCORE_SLICING";
            }
            if (lower.contains("invariant") || lower.contains("us-core-") || lower.contains("rule")) {
                return "USCORE_INVARIANT";
            }
            if (lower.contains("must support")) {
                return "USCORE_MUST_SUPPORT";
            }
            if (lower.contains("minimum allowed") || lower.contains("missing required")
                    || lower.contains("at least") || lower.contains("min cardinality")) {
                return "USCORE_MANDATORY_FIELD";
            }
            return "USCORE_PROFILE_CONFORMANCE";
        }
    }

    public String cleanMessage(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        String cleaned = HAPI_CODE_PREFIX.matcher(rawMessage).replaceFirst("");
        return cleaned.trim();
    }

    public String generateSuggestion(String ruleId, String path, String cleanedMessage) {
        return switch (ruleId) {
            case "STRUCT_PRIMITIVE_FORMAT" -> "Ensure the value conforms to the FHIR R4 primitive specification (e.g. YYYY-MM-DD for date).";
            case "STRUCT_UNKNOWN_ELEMENT" -> "Remove unrecognized elements or ensure extensions are declared under the 'extension' array.";
            case "STRUCT_CARDINALITY" -> "Ensure all mandatory base FHIR elements are populated and array counts are within bounds.";
            case "STRUCT_DATATYPE" -> "Ensure the field value matches the data type defined in the FHIR R4 schema.";
            case "USCORE_MANDATORY_FIELD" -> "Provide the required US Core element defined by the Implementation Guide profile.";
            case "USCORE_SLICING" -> "Verify that array elements declare the expected slice discriminator (e.g. required category code).";
            case "USCORE_INVARIANT" -> "Review the US Core profile invariant rule and update the resource fields accordingly.";
            case "USCORE_MUST_SUPPORT" -> "Ensure Must Support elements are populated if data is known, or specify dataAbsentReason.";
            default -> "Review the element against FHIR R4 and US Core specifications.";
        };
    }

    private String extractResourceType(String path, String fallback) {
        if (path != null && !path.isBlank()) {
            Matcher matcher = RESOURCE_TYPE_EXTRACTOR.matcher(path);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.equalsIgnoreCase("Bundle") || fallback == null) {
                    return candidate;
                }
            }
        }
        return fallback;
    }
}
