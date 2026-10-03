package org.fhirlint.core.comparison;

/**
 * Outcome of evaluating regression quality gates on a ComparisonReport.
 */
public record ComparisonGateResult(
    boolean passed,
    boolean scoreDropViolated,
    boolean regressionViolated,
    String failureReason
) {
    public static ComparisonGateResult pass() {
        return new ComparisonGateResult(true, false, false, "");
    }

    public static ComparisonGateResult failure(boolean scoreDropViolated, boolean regressionViolated, String failureReason) {
        return new ComparisonGateResult(false, scoreDropViolated, regressionViolated, failureReason != null ? failureReason : "");
    }
}
