package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.QualityIssue;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

/**
 * Engine contract for executing data quality rules across a dataset.
 */
public interface QualityRuleEngine {

    /**
     * Evaluates all registered quality rules against the given dataset and pre-computed graph index.
     *
     * @param dataset list of parsed FHIR resources
     * @param graphIndex pre-computed graph relationship index
     * @return list of detected quality issues
     */
    List<QualityIssue> evaluate(List<? extends IBaseResource> dataset, ResourceGraphIndex graphIndex);
}
