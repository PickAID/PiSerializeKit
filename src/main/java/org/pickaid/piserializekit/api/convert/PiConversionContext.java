package org.pickaid.piserializekit.api.convert;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class PiConversionContext {
    private static final PiConversionContext VALUE = new PiConversionContext("value", UnaryOperator.identity());

    private final String fieldName;
    private final UnaryOperator<Object> unwrapper;

    private PiConversionContext(String fieldName, UnaryOperator<Object> unwrapper) {
        this.fieldName = checkFieldName(fieldName);
        this.unwrapper = Objects.requireNonNull(unwrapper, "unwrapper");
    }

    public static PiConversionContext value() {
        return VALUE;
    }

    public static PiConversionContext field(String fieldName) {
        return new PiConversionContext(fieldName, UnaryOperator.identity());
    }

    public PiConversionContext withUnwrapper(UnaryOperator<Object> unwrapper) {
        return new PiConversionContext(fieldName, unwrapper);
    }

    public String fieldName() {
        return fieldName;
    }

    public Object unwrap(Object value) {
        return unwrapper.apply(value);
    }

    private static String checkFieldName(String fieldName) {
        String checked = Objects.requireNonNull(fieldName, "fieldName").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        return checked;
    }
}
