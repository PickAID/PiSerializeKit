package org.pickaid.piserializekit.api.runtime;

/**
 * Runtime failure raised when a required Pi runtime binding or serializer cannot be resolved.
 */
public final class PiRuntimeLookupException extends PiRuntimeException {
    private final String key;

    public PiRuntimeLookupException(String category, String key, String message) {
        super(category, message);
        this.key = key;
    }

    /**
     * Stable lookup key that could not be resolved.
     */
    public String key() {
        return key;
    }
}
