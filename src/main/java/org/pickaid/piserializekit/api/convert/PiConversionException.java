package org.pickaid.piserializekit.api.convert;

public final class PiConversionException extends IllegalArgumentException {
    public PiConversionException(String message) {
        super(message);
    }

    public PiConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
