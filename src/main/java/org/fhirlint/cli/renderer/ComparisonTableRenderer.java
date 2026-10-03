package org.fhirlint.cli.renderer;

import org.fhirlint.core.comparison.ComparisonGateResult;
import org.fhirlint.core.comparison.ComparisonReport;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.QualityScore;

import java.util.Map;

/**
 * Renders colorized ANSI differential reports for interactive terminal sessions.
 */
public class ComparisonTableRenderer {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    public String render(ComparisonReport report, boolean verbose, ComparisonGateResult gateResult) {
        StringBuilder sb = new StringBuilder();

        int baseScore = report.baseline().getQualityScore().getOverallScore();
        int targetScore = report.target().getQualityScore().getOverallScore();
        int delta = report.scoreDelta();

        // 1. Header Box
        sb.append("\n");
        sb.append(CYAN).append("══════════════════════════════════════════════════════════════════════════════").append(RESET).append("\n");
        sb.append(BOLD).append("                 FHIRLint Dataset Comparison & Regression Diff                ").append(RESET).append("\n");
        sb.append(CYAN).append("══════════════════════════════════════════════════════════════════════════════").append(RESET).append("\n");

        // 2. Score Badge
        String deltaStr;
        if (delta > 0) {
            deltaStr = GREEN + BOLD + "+" + delta + " (IMPROVED)" + RESET;
        } else if (delta < 0) {
            deltaStr = RED + BOLD + delta + " (REGRESSION)" + RESET;
        } else {
            deltaStr = GRAY + BOLD + "0 (UNCHANGED)" + RESET;
        }

        sb.append(String.format("  Baseline Score: %s%d/100 (%s)%s  ─▶  Target Score: %s%d/100 (%s)%s\n",
                BOLD, baseScore, report.baseline().getQualityScore().getGrade(), RESET,
                BOLD, targetScore, report.target().getQualityScore().getGrade(), RESET
        ));
        sb.append(String.format("  Quality Delta:  %s\n", deltaStr));

        if (gateResult != null && !gateResult.passed()) {
            sb.append(String.format("  Gate Status:    %s%sFAILED: %s%s\n", BOLD, RED, gateResult.failureReason(), RESET));
        }

        // 3. Issue Deltas Summary
        sb.append("\n");
        sb.append(String.format("  New Defects:      %s%d errors%s, %s%d warnings%s\n",
                report.getNewErrorCount() > 0 ? RED + BOLD : GRAY, report.getNewErrorCount(), RESET,
                report.getNewWarningCount() > 0 ? YELLOW + BOLD : GRAY, report.getNewWarningCount(), RESET
        ));
        sb.append(String.format("  Resolved Defects: %s%d errors%s, %s%d warnings%s\n",
                report.getResolvedErrorCount() > 0 ? GREEN + BOLD : GRAY, report.getResolvedErrorCount(), RESET,
                report.getResolvedWarningCount() > 0 ? GREEN : GRAY, report.getResolvedWarningCount(), RESET
        ));
        sb.append(String.format("  Persistent:       %d errors, %d warnings\n",
                report.getPersistentErrorCount(), report.getPersistentWarningCount()
        ));

        // 4. Resource Counts
        int baseTotal = report.baseline().inventory() != null ? report.baseline().inventory().totalResources() : 0;
        int targetTotal = report.target().inventory() != null ? report.target().inventory().totalResources() : 0;
        int totalDelta = report.resourceCountDeltas().getOrDefault("TOTAL", targetTotal - baseTotal);
        String resDeltaStr = totalDelta > 0 ? "+" + totalDelta : String.valueOf(totalDelta);

        sb.append(String.format("  Total Resources:  %d ─▶ %d (%s%s%s)\n",
                baseTotal, targetTotal, BOLD, resDeltaStr, RESET
        ));

        // 5. Category Breakdown Table
        sb.append("\n  ").append(BOLD).append("Category Deltas:").append(RESET).append("\n");
        sb.append(String.format("    %-24s %-12s %-12s %-12s\n", "CATEGORY", "BASELINE", "TARGET", "DELTA"));
        sb.append(GRAY).append("    ────────────────────────────────────────────────────────────").append(RESET).append("\n");
        for (Map.Entry<IssueCategory, Integer> entry : report.categoryDeltas().entrySet()) {
            IssueCategory cat = entry.getKey();
            int catDelta = entry.getValue();
            int bCat = report.baseline().getQualityScore().getCategoryScores().getOrDefault(cat, 100);
            int tCat = report.target().getQualityScore().getCategoryScores().getOrDefault(cat, 100);

            String catDeltaFmt;
            if (catDelta > 0) {
                catDeltaFmt = GREEN + "+" + catDelta + RESET;
            } else if (catDelta < 0) {
                catDeltaFmt = RED + String.valueOf(catDelta) + RESET;
            } else {
                catDeltaFmt = GRAY + "0" + RESET;
            }
            sb.append(String.format("    %-24s %-12d %-12d %-12s\n", cat.getDisplayName(), bCat, tCat, catDeltaFmt));
        }

        // 6. New Issues (Regressions) Detail
        if (!report.newIssues().isEmpty()) {
            sb.append("\n  ").append(BOLD).append(RED).append("Newly Introduced Regressions:").append(RESET).append("\n");
            for (QualityIssue issue : report.newIssues()) {
                String resId = issue.resourceId() != null ? issue.resourceId() : "unidentified";
                String path = issue.path() != null ? issue.path() : "root";
                sb.append(String.format("    [%s%s%s] (%s/%s: %s)\n",
                        issue.severity() == org.fhirlint.core.model.Severity.ERROR ? RED : YELLOW,
                        issue.ruleId(),
                        RESET,
                        issue.resourceType() != null ? issue.resourceType() : "Resource",
                        resId,
                        path
                ));
                sb.append("      ").append(issue.message()).append("\n");
                if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                    sb.append("      ").append(GRAY).append("Suggestion: ").append(issue.suggestion()).append(RESET).append("\n");
                }
            }
        }

        // 7. Resolved Issues Detail (if verbose)
        if (verbose && !report.resolvedIssues().isEmpty()) {
            sb.append("\n  ").append(BOLD).append(GREEN).append("Resolved Defects:").append(RESET).append("\n");
            for (QualityIssue issue : report.resolvedIssues()) {
                String resId = issue.resourceId() != null ? issue.resourceId() : "unidentified";
                sb.append(String.format("    [%s%s%s] %s/%s: %s\n",
                        GREEN, issue.ruleId(), RESET,
                        issue.resourceType() != null ? issue.resourceType() : "Resource",
                        resId,
                        issue.message()
                ));
            }
        }

        sb.append("\n");
        sb.append(GRAY).append("  ").append(QualityScore.NON_CLINICAL_DISCLAIMER).append(RESET).append("\n\n");
        return sb.toString();
    }
}
