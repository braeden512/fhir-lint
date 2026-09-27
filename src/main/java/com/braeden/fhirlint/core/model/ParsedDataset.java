package com.braeden.fhirlint.core.model;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Collections;
import java.util.List;

/**
 * Result of parsing and unrolling a FHIR JSON dataset into memory.
 */
public record ParsedDataset(
    IBaseResource rootResource,
    List<IBaseResource> resources,
    IngestionInventory inventory,
    boolean isBundle
) {
    public ParsedDataset {
        resources = resources == null ? Collections.emptyList() : Collections.unmodifiableList(resources);
    }
}
