package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * One decode issue entry.
 *
 * @param path issue path
 * @param code structured issue category
 * @param message issue message
 * @param fatal whether the issue is fatal
 */
public record PiDecodeIssue(String path, PiDecodeIssueCode code, String message, boolean fatal) {
    public PiDecodeIssue(String path, String message, boolean fatal) {
        this(path, PiDecodeIssueCode.UNKNOWN, message, fatal);
    }

    public PiDecodeIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
