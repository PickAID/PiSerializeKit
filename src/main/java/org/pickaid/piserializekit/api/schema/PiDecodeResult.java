package org.pickaid.piserializekit.api.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Collected decode issues for one schema read.
 */
public final class PiDecodeResult {
    private final List<PiDecodeIssue> issues = new ArrayList<>();

    /**
     * Adds one decode issue.
     *
     * @param issue decode issue
     */
    public void add(PiDecodeIssue issue) {
        issues.add(Objects.requireNonNull(issue, "issue"));
    }

    /**
     * Returns immutable decode issues.
     *
     * @return decode issues
     */
    public List<PiDecodeIssue> issues() {
        return List.copyOf(issues);
    }

    /**
     * Returns whether any issue was recorded.
     *
     * @return true when issues exist
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * Returns whether any fatal issue was recorded.
     *
     * @return true when a fatal issue exists
     */
    public boolean hasFatal() {
        return issues.stream().anyMatch(PiDecodeIssue::fatal);
    }

    /**
     * Returns a compact human-readable issue summary.
     *
     * @return compact summary
     */
    public String summary() {
        return issues.stream()
                .map(issue -> issue.path() + " -> " + issue.message())
                .collect(Collectors.joining("; "));
    }
}
