package org.pickaid.piserializekit.api.convert;

@FunctionalInterface
public interface PiValueConverter<T> {
    T convert(Object value, PiConversionContext context);

    default T convert(Object value) {
        return convert(value, PiConversionContext.value());
    }
}
