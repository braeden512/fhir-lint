package org.fhirlint.core.comparison;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;

import java.util.*;

/**
 * Pure in-memory comparator for evaluating quality score deltas, resource shifts,
 * and issue regressions between two LintReports.
 */
public final class DatasetComparator {

    private DatasetComparator() {}

    /**
     * Compares a baseline LintReport against a target LintReport.
     *
     * @param baseline reference baseline report (must not be null)
     * @param target target dataset report (must not be null)
     * @return comparison differential report
     */
    public static ComparisonReport compare(LintReport baseline, LintReport target) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(target, "target must not be null");

        // 1. Overall Score Delta
        int scoreDelta = target.getQualityScore().getOverallScore() - baseline.getQualityScore().getOverallScore();

        // 2. Category Score Deltas
        Map<IssueCategory, Integer> categoryDeltas = new EnumMap<>(IssueCategory.class);
        Map<IssueCategory, Integer> baseCatScores = baseline.getQualityScore().getCategoryScores();
        Map<IssueCategory, Integer> targetCatScores = target.getQualityScore().getCategoryScores();
        for (IssueCategory category : IssueCategory.values()) {
            int baseScore = baseCatScores.getOrDefault(category, 100);
            int targetScore = targetCatScores.getOrDefault(category, 100);
            categoryDeltas.put(category, targetScore - baseScore);
        }

        // 3. Resource Count Deltas
        Map<String, Integer> resourceCountDeltas = new LinkedHashMap<>();
        Map<String, Integer> baseCounts = baseline.inventory() != null ? baseline.inventory().resourceTypeCounts() : Collections.emptyMap();
        Map<String, Integer> targetCounts = target.inventory() != null ? target.inventory().resourceTypeCounts() : Collections.emptyMap();

        Set<String> allTypes = new TreeSet<>(baseCounts.keySet());
        allTypes.addAll(targetCounts.keySet());

        for (String type : allTypes) {
            int baseCount = baseCounts.getOrDefault(type, 0);
            int targetCount = targetCounts.getOrDefault(type, 0);
            resourceCountDeltas.put(type, targetCount - baseCount);
        }

        int totalBase = baseline.inventory() != null ? baseline.inventory().totalResources() : 0;
        int totalTarget = target.inventory() != null ? target.inventory().totalResources() : 0;
        resourceCountDeltas.put("TOTAL", totalTarget - totalBase);

        // 4. Issue Diffing using canonical IssueIdentityKey
        Map<IssueIdentityKey, QualityIssue> baseIssues = new LinkedHashMap<>();
        for (QualityIssue issue : baseline.getIssues()) {
            baseIssues.put(IssueIdentityKey.of(issue), issue);
        }

        Map<IssueIdentityKey, QualityIssue> targetIssues = new LinkedHashMap<>();
        for (QualityIssue issue : target.getIssues()) {
            targetIssues.put(IssueIdentityKey.of(issue), issue);
        }

        List<QualityIssue> newIssues = new ArrayList<>();
        List<QualityIssue> persistentIssues = new ArrayList<>();
        for (Map.Entry<IssueIdentityKey, QualityIssue> entry : targetIssues.entrySet()) {
            if (baseIssues.containsKey(entry.getKey())) {
                persistentIssues.add(entry.getValue());
            } else {
                newIssues.add(entry.getValue());
            }
        }

        List<QualityIssue> resolvedIssues = new ArrayList<>();
        for (Map.Entry<IssueIdentityKey, QualityIssue> entry : baseIssues.entrySet()) {
            if (!targetIssues.containsKey(entry.getKey())) {
                resolvedIssues.add(entry.getValue());
            }
        }

        return new ComparisonReport(
                baseline,
                target,
                scoreDelta,
                categoryDeltas,
                resourceCountDeltas,
                newIssues,
                resolvedIssues,
                persistentIssues
        );
    }
}
