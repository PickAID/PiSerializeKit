package org.pickaid.piserializekit.api.runtime;

/**
 * Base runtime failure exposed by Pi registries, bootstrap helpers, and service lookups.
 */
public class PiRuntimeException extends IllegalStateException {
    private final String category;

    public PiRuntimeException(String category, String message) {
        super(message);
        this.category = category;
    }

    public PiRuntimeException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    /**
     * Stable machine-readable category for tooling and higher runtime layers.
     */
    public final String category() {
        return category;
    }
}
