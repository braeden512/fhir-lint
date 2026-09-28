package org.fhirlint.core.graph;

import org.fhirlint.core.model.QualityIssue;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;

import java.util.List;

/**
 * Service interface for dataset-level referential integrity and resource graph analysis.
 */
public interface ReferentialIntegrityEngine {

    /**
     * Creates a new instance of the default in-memory ReferentialIntegrityEngine.
     */
    static ReferentialIntegrityEngine create() {
        return new DefaultReferentialIntegrityEngine();
    }

    /**
     * Analyzes a collection of in-memory FHIR resources, constructing a ResourceGraphIndex
     * and evaluating all inter-resource references for defects.
     *
     * @param resources List of parsed FHIR resources to analyze
     * @return List of detected QualityIssue findings in category REFERENTIAL_INTEGRITY
     */
    List<QualityIssue> analyze(List<? extends IBaseResource> resources);

    /**
     * Analyzes entries within a FHIR Bundle resource.
     *
     * @param bundle Parsed Bundle resource
     * @return List of detected QualityIssue findings in category REFERENTIAL_INTEGRITY
     */
    List<QualityIssue> analyze(Bundle bundle);

    /**
     * Analyzes an existing pre-built ResourceGraphIndex for defects.
     *
     * @param graphIndex Pre-built in-memory ResourceGraphIndex
     * @return List of detected QualityIssue findings in category REFERENTIAL_INTEGRITY
     */
    List<QualityIssue> analyze(ResourceGraphIndex graphIndex);

    /**
     * Builds and returns an indexed ResourceGraphIndex from the provided resources.
     *
     * @param resources List of parsed FHIR resources
     * @return In-memory ResourceGraphIndex
     */
    ResourceGraphIndex buildIndex(List<? extends IBaseResource> resources);
}
