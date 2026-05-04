package org.pickaid.piserializekit.api.inspect;

import java.util.Objects;

/**
 * One structured object inspection issue.
 *
 * @param path object graph path
 * @param code issue category
 * @param message issue message
 * @param fatal whether this issue should fail the checked object
 */
public record PiInspectionIssue(String path, PiInspectionIssueCode code, String message, boolean fatal) {
    /**
     * Creates one unclassified inspection issue.
     *
     * @param path object graph path
     * @param message issue message
     * @param fatal whether this issue should fail the checked object
     */
    public PiInspectionIssue(String path, String message, boolean fatal) {
        this(path, PiInspectionIssueCode.UNKNOWN, message, fatal);
    }

    /**
     * Canonical constructor with null validation.
     */
    public PiInspectionIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
