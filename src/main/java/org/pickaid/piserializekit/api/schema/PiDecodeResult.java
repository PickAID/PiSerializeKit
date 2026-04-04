package org.pickaid.piserializekit.api.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PiDecodeResult {
    private final List<PiDecodeIssue> issues = new ArrayList<>();

    public void add(PiDecodeIssue issue) {
        issues.add(Objects.requireNonNull(issue, "issue"));
    }

    public List<PiDecodeIssue> issues() {
        return List.copyOf(issues);
    }

    public boolean hasFatal() {
        return issues.stream().anyMatch(PiDecodeIssue::fatal);
    }
}
