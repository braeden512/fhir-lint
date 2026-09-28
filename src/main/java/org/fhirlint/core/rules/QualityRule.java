package org.fhirlint.core.rules;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;

import java.util.List;
import java.util.Set;

/**
 * Base contract for pluggable healthcare data quality checks.
 */
public interface QualityRule {

    /**
     * Unique identifier for this rule (e.g. "CONS-001", "DUP-001").
     */
    String getRuleId();

    /**
     * Human-readable display name for reporting.
     */
    String getName();

    /**
     * Category of quality defect evaluated by this rule.
     */
    IssueCategory getCategory();

    /**
     * Default severity level assigned to issues emitted by this rule.
     */
    Severity getDefaultSeverity();

    /**
     * Execution scope (RESOURCE for per-resource evaluation, DATASET for global population analysis).
     */
    RuleScope getScope();

    /**
     * Resource types to which this rule applies (e.g. Set.of("Encounter", "Coverage")).
     * An empty set denotes universal applicability across all resource types.
     */
    Set<String> getApplicableResourceTypes();

    /**
     * Evaluates this rule against the provided context.
     *
     * @param context the evaluation environment exposing the resource, dataset, graph index, and terminology
     * @return list of detected quality issues (empty if no issues found)
     */
    List<QualityIssue> evaluate(RuleContext context);
}
