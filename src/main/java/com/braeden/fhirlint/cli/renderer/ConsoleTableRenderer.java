package com.braeden.fhirlint.cli.renderer;

import com.braeden.fhirlint.core.model.IssueCategory;
import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.QualityScore;

import java.util.Map;

/**
 * Renders colorized ANSI summary reports for interactive terminal sessions.
 */
public class ConsoleTableRenderer {

    // ANSI escape codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    public String render(LintReport report, boolean verbose) {
        StringBuilder sb = new StringBuilder();
        QualityScore score = report.qualityScore();

        // 1. Header Box
        sb.append("\n");
        sb.append(CYAN).append("══════════════════════════════════════════════════════════════════════════════").append(RESET).append("\n");
        sb.append(BOLD).append("                        FHIRLint Healthcare Data Quality                      ").append(RESET).append("\n");
        sb.append(CYAN).append("══════════════════════════════════════════════════════════════════════════════").append(RESET).append("\n");

        // 2. Score Badge
        String gradeColor = switch (score.getGrade()) {
            case EXCELLENT -> GREEN;
            case ACCEPTABLE -> BLUE;
            case DEGRADED -> YELLOW;
            case CRITICAL -> RED;
        };

        sb.append(String.format("  Quality Score: %s%s%d/100 (%s)%s   Target Profile: %s%s%s\n",
            BOLD, gradeColor, score.getOverallScore(), score.getGrade(), RESET,
            BOLD, report.targetProfile(), RESET
        ));
        sb.append(String.format("  Grade Status:  %s%s%s\n",
            GRAY, score.getGrade().getDescription(), RESET
        ));

        // 3. Metrics Summary
        sb.append("\n");
        sb.append(String.format("  Resources: %s%d%s  │  Errors: %s%d%s  │  Warnings: %s%d%s  │  Duration: %s%dms%s\n",
            BOLD, report.inventory().totalResources(), RESET,
            score.getErrorCount() > 0 ? RED + BOLD : GRAY, score.getErrorCount(), RESET,
            score.getWarningCount() > 0 ? YELLOW + BOLD : GRAY, score.getWarningCount(), RESET,
            GRAY, report.durationMs(), RESET
        ));

        // 4. Resource Type Breakdown
        if (!report.inventory().resourceTypeCounts().isEmpty()) {
            sb.append("\n  ").append(BOLD).append("Resource Distribution:").append(RESET).append("\n");
            for (Map.Entry<String, Integer> entry : report.inventory().resourceTypeCounts().entrySet()) {
                sb.append(String.format("    %-20s : %d\n", entry.getKey(), entry.getValue()));
            }
        }

        // 5. Category Breakdown Table
        sb.append("\n  ").append(BOLD).append("Category Breakdown:").append(RESET).append("\n");
        for (Map.Entry<IssueCategory, Integer> entry : score.getCategoryScores().entrySet()) {
            IssueCategory cat = entry.getKey();
            int catScore = entry.getValue();
            String bar = renderProgressBar(catScore);
            String catColor = catScore >= 90 ? GREEN : (catScore >= 75 ? BLUE : (catScore >= 50 ? YELLOW : RED));

            sb.append(String.format("    %-28s [%s] %s%3d%%%s\n",
                cat.getDisplayName(), bar, catColor, catScore, RESET
            ));
        }

        // 6. Issues Listing
        if (!report.issues().isEmpty()) {
            sb.append("\n  ").append(BOLD).append("Findings:").append(RESET).append("\n");
            int displayCount = verbose ? report.issues().size() : Math.min(report.issues().size(), 10);

            for (int i = 0; i < displayCount; i++) {
                QualityIssue issue = report.issues().get(i);
                String sevColor = switch (issue.severity()) {
                    case ERROR -> RED;
                    case WARNING -> YELLOW;
                    case INFO -> BLUE;
                };

                sb.append(String.format("    %s[%-7s]%s %s%-8s%s %s (%s)\n",
                    sevColor, issue.severity(), RESET,
                    BOLD, issue.ruleId(), RESET,
                    issue.message(),
                    issue.path() != null ? issue.path() : "root"
                ));
                if (issue.suggestion() != null) {
                    sb.append(String.format("      %s→ Suggestion: %s%s\n", GRAY, issue.suggestion(), RESET));
                }
            }

            if (!verbose && report.issues().size() > 10) {
                sb.append(String.format("    %s... and %d more findings. Use --verbose to view all.%s\n",
                    GRAY, report.issues().size() - 10, RESET
                ));
            }
        } else {
            sb.append("\n  ").append(GREEN).append("✔ No quality defects or schema violations detected.").append(RESET).append("\n");
        }

        sb.append(CYAN).append("══════════════════════════════════════════════════════════════════════════════").append(RESET).append("\n");
        return sb.toString();
    }

    private String renderProgressBar(int score) {
        int totalBlocks = 20;
        int filledBlocks = (int) Math.round((score / 100.0) * totalBlocks);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < totalBlocks; i++) {
            if (i < filledBlocks) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
    }
}
