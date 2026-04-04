package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

public final class PiDecodeContext {
    private final PiDecodeResult result = new PiDecodeResult();

    private PiDecodeContext() {
    }

    public static PiDecodeContext strict() {
        return new PiDecodeContext();
    }

    public void issue(String path, String message, boolean fatal) {
        result.add(new PiDecodeIssue(Objects.requireNonNull(path, "path"), Objects.requireNonNull(message, "message"), fatal));
    }

    public PiDecodeResult result() {
        return result;
    }
}
