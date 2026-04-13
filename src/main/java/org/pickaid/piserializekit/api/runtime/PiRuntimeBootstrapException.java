package org.pickaid.piserializekit.api.runtime;

/**
 * Runtime failure raised while discovering or instantiating Pi runtime providers.
 */
public final class PiRuntimeBootstrapException extends PiRuntimeException {
    private final String providerClassName;

    public PiRuntimeBootstrapException(String category, String providerClassName, String message) {
        super(category, message);
        this.providerClassName = providerClassName;
    }

    public PiRuntimeBootstrapException(String category, String providerClassName, String message, Throwable cause) {
        super(category, message, cause);
        this.providerClassName = providerClassName;
    }

    /**
     * Provider class involved in the bootstrap failure when one was known.
     */
    public String providerClassName() {
        return providerClassName;
    }
}
