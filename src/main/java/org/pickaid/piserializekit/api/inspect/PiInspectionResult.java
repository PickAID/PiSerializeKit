package org.pickaid.piserializekit.api.inspect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Collected object inspection issues.
 */
public final class PiInspectionResult {
    private List<PiInspectionIssue> issues;
    private boolean hasFatal;

    /**
     * Adds one inspection issue.
     *
     * @param issue inspection issue
     */
    public void add(PiInspectionIssue issue) {
        PiInspectionIssue checked = Objects.requireNonNull(issue, "issue");
        if (issues == null) {
            issues = new ArrayList<>(1);
        }
        issues.add(checked);
        hasFatal |= checked.fatal();
    }

    /**
     * Adds one inspection issue.
     *
     * @param path object graph path
     * @param code issue category
     * @param message issue message
     * @param fatal whether this issue should fail the checked object
     */
    public void issue(String path, PiInspectionIssueCode code, String message, boolean fatal) {
        add(new PiInspectionIssue(path, code, message, fatal));
    }

    /**
     * Returns immutable inspection issues.
     *
     * @return inspection issues
     */
    public List<PiInspectionIssue> issues() {
        return issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * Returns whether any issue was recorded.
     *
     * @return true when issues exist
     */
    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }

    /**
     * Returns whether any fatal issue was recorded.
     *
     * @return true when a fatal issue exists
     */
    public boolean hasFatal() {
        return hasFatal;
    }

    /**
     * Returns whether any issue was already recorded for the exact path.
     *
     * @param path absolute object path
     * @return true when at least one issue matches the path
     */
    public boolean hasIssueAt(String path) {
        Objects.requireNonNull(path, "path");
        if (issues == null) {
            return false;
        }
        return issues.stream().anyMatch(issue -> issue.path().equals(path));
    }

    /**
     * Returns a compact human-readable issue summary.
     *
     * @return compact summary
     */
    public String summary() {
        return summary(3);
    }

    /**
     * Returns a compact human-readable issue summary capped to the supplied number of entries.
     *
     * @param maxEntries maximum rendered entries
     * @return compact summary
     */
    public String summary(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        List<PiInspectionIssue> ordered = summarizedIssues();
        int limit = Math.min(maxEntries, ordered.size());
        String summary = ordered.subList(0, limit).stream()
                .map(issue -> issue.path() + " -> " + issue.message())
                .collect(Collectors.joining("; "));
        int remaining = ordered.size() - limit;
        if (remaining > 0) {
            return summary + "; +" + remaining + " more";
        }
        return summary;
    }

    /**
     * Returns one author-facing severity label for the collected result.
     *
     * @return "fatal" when any fatal issue exists, otherwise "non-fatal"
     */
    public String severityLabel() {
        return hasFatal() ? "fatal" : "non-fatal";
    }

    /**
     * Returns one compact author-facing summary grouped by path.
     *
     * @return grouped author summary
     */
    public String authorSummary() {
        return authorSummary(3);
    }

    /**
     * Returns one compact author-facing summary grouped by path and capped to the supplied number of entries.
     *
     * @param maxEntries maximum rendered grouped entries
     * @return grouped author summary
     */
    public String authorSummary(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        List<AuthorSummaryEntry> entries = summarizedAuthorEntries();
        int limit = Math.min(maxEntries, entries.size());
        String summary = entries.subList(0, limit).stream()
                .map(AuthorSummaryEntry::render)
                .collect(Collectors.joining("; "));
        int remaining = entries.size() - limit;
        if (remaining > 0) {
            return summary + "; +" + remaining + " more";
        }
        return summary;
    }

    private List<PiInspectionIssue> summarizedIssues() {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<PiInspectionIssue> fatal = new ArrayList<>();
        List<PiInspectionIssue> nonFatal = new ArrayList<>();
        for (PiInspectionIssue issue : issues) {
            String key = issue.path() + "\u0000" + issue.message();
            if (!seen.add(key)) {
                continue;
            }
            if (issue.fatal()) {
                fatal.add(issue);
            } else {
                nonFatal.add(issue);
            }
        }
        List<PiInspectionIssue> ordered = new ArrayList<>(fatal.size() + nonFatal.size());
        ordered.addAll(fatal);
        ordered.addAll(nonFatal);
        return ordered;
    }

    private List<AuthorSummaryEntry> summarizedAuthorEntries() {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        List<PathIssueGroup> groups = new ArrayList<>();
        for (PiInspectionIssue issue : issues) {
            PathIssueGroup group = groups.stream()
                    .filter(candidate -> candidate.path().equals(issue.path()))
                    .findFirst()
                    .orElseGet(() -> {
                        PathIssueGroup created = new PathIssueGroup(issue.path(), groups.size());
                        groups.add(created);
                        return created;
                    });
            group.add(issue);
        }
        return groups.stream()
                .map(PathIssueGroup::toEntry)
                .sorted(Comparator
                        .comparing(AuthorSummaryEntry::fatal)
                        .reversed()
                        .thenComparingInt(AuthorSummaryEntry::order))
                .toList();
    }

    private record AuthorSummaryEntry(String path, String message, boolean fatal, int order, int secondaryCount) {
        private String render() {
            if (secondaryCount > 0) {
                return path + " -> " + message + " (+" + secondaryCount + " more)";
            }
            return path + " -> " + message;
        }
    }

    private static final class PathIssueGroup {
        private final String path;
        private final int order;
        private final List<PiInspectionIssue> uniqueIssues = new ArrayList<>();
        private final LinkedHashSet<String> seen = new LinkedHashSet<>();

        private PathIssueGroup(String path, int order) {
            this.path = path;
            this.order = order;
        }

        private String path() {
            return path;
        }

        private void add(PiInspectionIssue issue) {
            String key = issue.code() + "\u0000" + issue.message() + "\u0000" + issue.fatal();
            if (seen.add(key)) {
                uniqueIssues.add(issue);
            }
        }

        private AuthorSummaryEntry toEntry() {
            PiInspectionIssue primary = uniqueIssues.stream()
                    .filter(PiInspectionIssue::fatal)
                    .findFirst()
                    .orElse(uniqueIssues.get(0));
            int secondaryCount = uniqueIssues.size() - 1;
            return new AuthorSummaryEntry(path, primary.message(), primary.fatal(), order, secondaryCount);
        }
    }
}
