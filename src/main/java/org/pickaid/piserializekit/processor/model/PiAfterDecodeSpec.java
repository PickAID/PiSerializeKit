package org.pickaid.piserializekit.processor.model;

public record PiAfterDecodeSpec(String methodName) {
    public static PiAfterDecodeSpec none() {
        return new PiAfterDecodeSpec(null);
    }

    public boolean present() {
        return methodName != null;
    }
}
