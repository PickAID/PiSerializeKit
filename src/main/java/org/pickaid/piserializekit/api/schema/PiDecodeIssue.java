package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

public record PiDecodeIssue(String path, String message, boolean fatal) {
    public PiDecodeIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }
}
