package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * Mutable decode diagnostics collector for one schema read.
 */
public final class PiDecodeContext {
    private final PiDecodeResult result;
    private final String prefix;

    private PiDecodeContext(PiDecodeResult result, String prefix) {
        this.result = Objects.requireNonNull(result, "result");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    public static PiDecodeContext strict() {
        return new PiDecodeContext(new PiDecodeResult(), "");
    }

    /**
     * Returns a child context that prefixes future issue paths.
     *
     * @param segment path segment relative to this context
     * @return shared-result child context
     */
    public PiDecodeContext child(String segment) {
        return new PiDecodeContext(result, resolvePath(Objects.requireNonNull(segment, "segment")));
    }

    /**
     * Records one structured decode issue.
     *
     * @param code issue category
     * @param path issue path
     * @param message issue message
     * @param fatal whether decoding should be considered fatal
     */
    public void issue(PiDecodeIssueCode code, String path, String message, boolean fatal) {
        result.add(new PiDecodeIssue(
                resolvePath(Objects.requireNonNull(path, "path")),
                Objects.requireNonNull(code, "code"),
                Objects.requireNonNull(message, "message"),
                fatal
        ));
    }

    /**
     * Records one decode issue with an unspecified category.
     *
     * @param path issue path
     * @param message issue message
     * @param fatal whether decoding should be considered fatal
     */
    public void issue(String path, String message, boolean fatal) {
        issue(PiDecodeIssueCode.UNKNOWN, path, message, fatal);
    }

    /**
     * Returns the accumulated decode result.
     *
     * @return decode result
     */
    public PiDecodeResult result() {
        return result;
    }

    private String resolvePath(String path) {
        if (prefix.isEmpty()) {
            return path.isEmpty() ? "$" : path;
        }
        if (path.isEmpty()) {
            return prefix;
        }
        if (path.startsWith("[")) {
            return prefix + path;
        }
        return prefix + "." + path;
    }
}
