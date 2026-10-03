package org.fhirlint.core.comparison;

import org.fhirlint.core.model.QualityIssue;
import java.util.Objects;

/**
 * Deterministic canonical identity tuple for matching quality issues across dataset versions.
 */
public record IssueIdentityKey(
    String ruleId,
    String resourceType,
    String resourceId,
    String path,
    int messageHash
) {
    public IssueIdentityKey {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        resourceType = resourceType != null ? resourceType : "__GLOBAL__";
        resourceId = resourceId != null ? resourceId : "__NO_ID__";
        path = path != null ? path : "__ROOT__";
    }

    /**
     * Constructs a canonical identity key from a QualityIssue.
     *
     * @param issue the quality issue
     * @return canonical identity key
     */
    public static IssueIdentityKey of(QualityIssue issue) {
        Objects.requireNonNull(issue, "issue must not be null");
        String resId = issue.resourceId() != null ? issue.resourceId() : "__NO_ID__";
        int msgHash = resId.equals("__NO_ID__") && issue.message() != null ? issue.message().hashCode() : 0;
        return new IssueIdentityKey(
            issue.ruleId(),
            issue.resourceType(),
            resId,
            issue.path(),
            msgHash
        );
    }
}
