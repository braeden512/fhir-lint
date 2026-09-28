package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.rules.terminology.TerminologyService;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Thread-safe evaluation context passed into QualityRule implementations.
 */
public class RuleContext {

    private final IBaseResource resource;
    private final List<IBaseResource> dataset;
    private final ResourceGraphIndex graphIndex;
    private final TerminologyService terminologyService;

    public RuleContext(
            IBaseResource resource,
            List<? extends IBaseResource> dataset,
            ResourceGraphIndex graphIndex,
            TerminologyService terminologyService) {
        this.resource = resource;
        this.dataset = dataset != null ? Collections.unmodifiableList(dataset) : Collections.emptyList();
        this.graphIndex = graphIndex;
        this.terminologyService = terminologyService;
    }

    /**
     * Returns the target resource when evaluating a RESOURCE-scoped rule, or empty if DATASET-scoped.
     */
    public Optional<IBaseResource> getResource() {
        return Optional.ofNullable(resource);
    }

    /**
     * Returns all resources in the active dataset.
     */
    public List<IBaseResource> getDataset() {
        return dataset;
    }

    /**
     * Returns the pre-computed ResourceGraphIndex for cross-resource relationship resolution.
     */
    public ResourceGraphIndex getGraphIndex() {
        return graphIndex;
    }

    /**
     * Returns the terminology service for code and system validation.
     */
    public TerminologyService getTerminologyService() {
        return terminologyService;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private IBaseResource resource;
        private List<? extends IBaseResource> dataset;
        private ResourceGraphIndex graphIndex;
        private TerminologyService terminologyService;

        public Builder resource(IBaseResource resource) {
            this.resource = resource;
            return this;
        }

        public Builder dataset(List<? extends IBaseResource> dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder graphIndex(ResourceGraphIndex graphIndex) {
            this.graphIndex = graphIndex;
            return this;
        }

        public Builder terminologyService(TerminologyService terminologyService) {
            this.terminologyService = terminologyService;
            return this;
        }

        public RuleContext build() {
            return new RuleContext(resource, dataset, graphIndex, terminologyService);
        }
    }
}
