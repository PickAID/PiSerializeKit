package org.pickaid.piserializekit.api.runtime;

/**
 * Runtime failure raised when Pi runtime registrations conflict with existing entries.
 */
public final class PiRuntimeConflictException extends PiRuntimeException {
    private final String key;

    public PiRuntimeConflictException(String category, String key, String message) {
        super(category, message);
        this.key = key;
    }

    /**
     * Stable conflicting lookup key.
     */
    public String key() {
        return key;
    }
}
