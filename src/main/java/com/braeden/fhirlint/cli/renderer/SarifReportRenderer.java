package com.braeden.fhirlint.cli.renderer;

import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.QualityIssue;
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

        ArrayNode results = run.putArray("results");
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
            artifactLoc.put("uri", targetFilePath != null ? targetFilePath : "input.json");

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
}
