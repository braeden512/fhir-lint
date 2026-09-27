package com.braeden.fhirlint.cli.renderer;

import com.braeden.fhirlint.core.model.LintReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serializes LintReport to formatted JSON.
 */
public class JsonReportRenderer {

    private final ObjectMapper objectMapper;

    public JsonReportRenderer() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String render(LintReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render report as JSON: " + e.getMessage(), e);
        }
    }
}
