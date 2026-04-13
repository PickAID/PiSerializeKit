package org.pickaid.piserializekit.api.runtime;

/**
 * Runtime validation failure raised when a manual or generated Pi binding is structurally invalid.
 *
 * <p>This remains an {@link IllegalArgumentException} because the immediate failure is still
 * caused by an invalid binding contract, but it also carries stable machine-readable context
 * for higher tooling layers.</p>
 */
public final class PiRuntimeBindingValidationException extends IllegalArgumentException {
    private final String category;
    private final String bindingId;

    public PiRuntimeBindingValidationException(String category, String bindingId, String message) {
        super(message);
        this.category = category;
        this.bindingId = bindingId;
    }

    public PiRuntimeBindingValidationException(String category, String bindingId, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.bindingId = bindingId;
    }

    /**
     * Stable validation category for tooling and diagnostics.
     */
    public String category() {
        return category;
    }

    /**
     * Stable binding id involved in the validation failure when one was available.
     */
    public String bindingId() {
        return bindingId;
    }
}
