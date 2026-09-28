package org.fhirlint.core.parser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirlint.core.model.IngestionInventory;
import org.fhirlint.core.model.ParsedDataset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fast, stateless HAPI FHIR parser with boundary pre-flight verification.
 */
public class FhirBundleParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FhirContext fhirContext;

    public FhirBundleParser() {
        this.fhirContext = FhirContext.forR4Cached();
    }

    public FhirBundleParser(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    /**
     * Parses FHIR JSON content from a File.
     */
    public ParsedDataset parse(File file) {
        if (!file.exists() || !file.isFile()) {
            throw new FhirParseException("File not found or not a valid file: " + file.getAbsolutePath());
        }
        try (InputStream is = new FileInputStream(file)) {
            return parse(is);
        } catch (IOException e) {
            throw new FhirParseException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Parses FHIR JSON content from an InputStream.
     */
    public ParsedDataset parse(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                throw new FhirParseException("Input stream is empty");
            }
            String jsonContent = new String(bytes, StandardCharsets.UTF_8);
            if (jsonContent.trim().isEmpty()) {
                throw new FhirParseException("Input stream is empty");
            }
            return parse(jsonContent);
        } catch (IOException e) {
            throw new FhirParseException("Failed to read input stream: " + e.getMessage(), e);
        }
    }

    /**
     * Parses FHIR JSON content from a String.
     */
    public ParsedDataset parse(String jsonContent) {
        long startTime = System.currentTimeMillis();

        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            throw new FhirParseException("Payload cannot be empty or null.");
        }

        // 1. Syntactic pre-flight check using Jackson
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(jsonContent);
        } catch (Exception e) {
            throw new FhirParseException("Malformed JSON payload: " + e.getMessage(), e);
        }

        if (rootNode == null || !rootNode.isObject()) {
            throw new FhirParseException("Payload must be a JSON object.");
        }

        JsonNode resourceTypeNode = rootNode.get("resourceType");
        if (resourceTypeNode == null || !resourceTypeNode.isTextual() || resourceTypeNode.asText().trim().isEmpty()) {
            throw new FhirParseException("Payload must contain a valid FHIR 'resourceType' declaration.");
        }

        // 2. Parse using HAPI FHIR with recording error handler
        IParser parser = fhirContext.newJsonParser();
        RecordingParserErrorHandler errorHandler = new RecordingParserErrorHandler();
        parser.setParserErrorHandler(errorHandler);
        IBaseResource parsedResource;
        try {
            parsedResource = parser.parseResource(jsonContent);
        } catch (Exception e) {
            throw new FhirParseException("HAPI FHIR failed to parse resource: " + e.getMessage(), e);
        }

        // 3. Unroll resources and extract inventory counts
        List<IBaseResource> resources = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        boolean isBundle = false;

        if (parsedResource instanceof Bundle bundle) {
            isBundle = true;
            if (bundle.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    Resource res = entry.getResource();
                    if (res != null) {
                        resources.add(res);
                        String type = res.fhirType();
                        counts.put(type, counts.getOrDefault(type, 0) + 1);
                    }
                }
            }
        } else {
            resources.add(parsedResource);
            String type = parsedResource.fhirType();
            counts.put(type, 1);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        IngestionInventory inventory = new IngestionInventory(resources.size(), counts, durationMs);

        return new ParsedDataset(parsedResource, resources, inventory, isBundle, errorHandler.getMessages());
    }
}
