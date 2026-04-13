package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * Mutable decode diagnostics collector for one schema read.
 */
public final class PiDecodeContext {
    private final PiDecodeContext root;
    private final PiDecodeContext parent;
    private final String segment;

    private PiDecodeResult result;

    private PiDecodeContext(PiDecodeContext root, PiDecodeContext parent, String segment) {
        this.root = root;
        this.parent = parent;
        this.segment = Objects.requireNonNull(segment, "segment");
    }

    public static PiDecodeContext strict() {
        return new PiDecodeContext(null, null, "");
    }

    /**
     * Returns a child context that prefixes future issue paths.
     *
     * @param segment path segment relative to this context
     * @return shared-result child context
     */
    public PiDecodeContext child(String segment) {
        return new PiDecodeContext(sharedRoot(), this, Objects.requireNonNull(segment, "segment"));
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
        sharedRoot().ensureResult().add(new PiDecodeIssue(
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
        return sharedRoot().ensureResult();
    }

    /**
     * Returns whether an issue was already recorded for the supplied relative path.
     *
     * @param path issue path relative to this context
     * @return true when one issue already exists at the resolved path
     */
    public boolean hasIssue(String path) {
        PiDecodeResult current = sharedRoot().result;
        return current != null && current.hasIssueAt(resolvePath(Objects.requireNonNull(path, "path")));
    }

    private PiDecodeContext sharedRoot() {
        return root == null ? this : root;
    }

    private PiDecodeResult ensureResult() {
        if (result == null) {
            result = new PiDecodeResult();
        }
        return result;
    }

    private String resolvePath(String path) {
        String prefix = prefix();
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

    private String prefix() {
        if (parent == null) {
            return "";
        }
        String parentPrefix = parent.prefix();
        if (parentPrefix.isEmpty()) {
            return segment;
        }
        if (segment.startsWith("[")) {
            return parentPrefix + segment;
        }
        return parentPrefix + "." + segment;
    }
}
