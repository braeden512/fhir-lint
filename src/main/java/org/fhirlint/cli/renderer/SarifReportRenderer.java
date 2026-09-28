package org.fhirlint.cli.renderer;

import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders LintReport as SARIF 2.1.0 JSON for GitHub PR code scanning.
 */
public class SarifReportRenderer {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String render(LintReport report, String targetFilePath) {
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
        root.put("version", "2.1.0");

        ArrayNode runs = root.putArray("runs");
        ObjectNode run = mapper.createObjectNode();
        runs.add(run);

        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", "FHIRLint");
        driver.put("version", "0.1.0");
        driver.put("informationUri", "https://github.com/braeden512/fhir-lint");

        ArrayNode rules = driver.putArray("rules");
        java.util.Set<String> declaredRuleIds = new java.util.HashSet<>();
        addRule(rules, declaredRuleIds, "REF-001", "Broken Local Reference", "Target resource does not exist in dataset (broken local/relative path, UUID URN, broken #contained fragment, or empty reference).");
        addRule(rules, declaredRuleIds, "REF-002", "Reference Type Mismatch", "Target resource exists, but its resource type is not permitted for the referencing field by FHIR R4 schema.");
        addRule(rules, declaredRuleIds, "REF-003", "Orphaned Clinical Resource", "Orphaned clinical resource lacks direct or indirect context link to a Patient.");
        addRule(rules, declaredRuleIds, "REF-004", "Unverified External Reference", "Reference points to an external absolute URI outside the dataset boundary during offline analysis.");
        addRule(rules, declaredRuleIds, "CONS-001", "Period Chronology Inversion", "Period end occurs chronologically before period start.");
        addRule(rules, declaredRuleIds, "CONS-002", "Birth-to-Event Chronological Inversion", "Clinical event date predates patient birth date.");
        addRule(rules, declaredRuleIds, "CONS-003", "Post-Mortem Event Inversion", "Clinical event date occurs after patient deceased date.");
        addRule(rules, declaredRuleIds, "CONS-004", "Diagnostic Report Observation Inconsistency", "Final DiagnosticReport references entered-in-error or cancelled observation.");
        addRule(rules, declaredRuleIds, "DUP-001", "Patient Identifier Collision", "Multiple distinct Patient resources share identical identifier system and value.");
        addRule(rules, declaredRuleIds, "DUP-002", "Probable Demographic Duplicate", "Multiple Patient resources share matching family name, given name, birth date, and postal code.");
        addRule(rules, declaredRuleIds, "TERM-001", "Non-Canonical System URI", "Coding uses invalid or non-canonical code system URI.");
        addRule(rules, declaredRuleIds, "TERM-002", "Missing Vital Signs UCUM Unit", "Vital signs observation missing canonical UCUM unit code or system.");
        addRule(rules, declaredRuleIds, "TERM-003", "Fixed Value Set Binding Violation", "Resource element code does not belong to the required core value set.");
        addRule(rules, declaredRuleIds, "COMP-001", "Missing Clinical Subject Reference", "Clinical resource is missing a mandatory subject reference linking it to a Patient.");
        addRule(rules, declaredRuleIds, "COMP-002", "Missing Observation Value or Absent Reason", "Observation has neither a value nor a dataAbsentReason.");

        // Dynamically register uncataloged rules
        for (QualityIssue issue : report.issues()) {
            String rId = issue.ruleId() != null ? issue.ruleId() : "UNKNOWN";
            if (!declaredRuleIds.contains(rId)) {
                String ruleName = issue.category() != null ? issue.category().getDisplayName() + " Rule" : rId;
                String desc = issue.message() != null ? issue.message() : ruleName;
                addRule(rules, declaredRuleIds, rId, ruleName, desc);
            }
        }

        ObjectNode properties = run.putObject("properties");
        properties.put("disclaimer", org.fhirlint.core.model.QualityScore.NON_CLINICAL_DISCLAIMER);

        ArrayNode results = run.putArray("results");
        String effectiveUri = (targetFilePath == null || targetFilePath.isBlank() || targetFilePath.equals("-"))
            ? "stdin"
            : targetFilePath.replace('\\', '/');

        for (QualityIssue issue : report.issues()) {
            ObjectNode resNode = mapper.createObjectNode();
            resNode.put("ruleId", issue.ruleId());
            resNode.put("level", switch (issue.severity()) {
                case ERROR -> "error";
                case WARNING -> "warning";
                case INFO -> "note";
            });

            ObjectNode messageNode = resNode.putObject("message");
            messageNode.put("text", issue.message() + (issue.suggestion() != null ? " Suggestion: " + issue.suggestion() : ""));

            ArrayNode locations = resNode.putArray("locations");
            ObjectNode loc = mapper.createObjectNode();
            locations.add(loc);

            ObjectNode physLoc = loc.putObject("physicalLocation");
            ObjectNode artifactLoc = physLoc.putObject("artifactLocation");
            artifactLoc.put("uri", effectiveUri);

            if (issue.path() != null) {
                ObjectNode region = physLoc.putObject("region");
                region.putObject("message").put("text", issue.path());
            }

            results.add(resNode);
        }

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render SARIF output: " + e.getMessage(), e);
        }
    }

    private void addRule(ArrayNode rules, java.util.Set<String> declaredRuleIds, String id, String name, String fullDescription) {
        if (declaredRuleIds.add(id)) {
            ObjectNode rule = rules.addObject();
            rule.put("id", id);
            rule.put("name", name);
            rule.putObject("shortDescription").put("text", name);
            rule.putObject("fullDescription").put("text", fullDescription);
        }
    }
}
