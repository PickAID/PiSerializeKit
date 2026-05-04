package org.pickaid.piserializekit.api.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class PiStringIdConverter implements PiValueConverter<String> {
    private final List<String> idFields;
    private final List<Source<?>> sources;
    private final boolean normalizeEnumName;

    private PiStringIdConverter(List<String> idFields, List<Source<?>> sources, boolean normalizeEnumName) {
        this.idFields = List.copyOf(idFields);
        this.sources = List.copyOf(sources);
        this.normalizeEnumName = normalizeEnumName;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String convert(Object value, PiConversionContext context) {
        Objects.requireNonNull(context, "context");
        return convertUnwrapped(context.unwrap(value), context);
    }

    private String convertUnwrapped(Object value, PiConversionContext context) {
        if (value == null) {
            throw new PiConversionException(context.fieldName() + " can't be null");
        }

        if (value instanceof JsonElement element) {
            return convertJson(element, context);
        }

        if (value instanceof Enum<?> enumValue) {
            return normalize(enumValue.name(), context);
        }

        for (Source<?> source : sources) {
            String id = source.tryRead(value);
            if (id != null) {
                return normalize(id, context);
            }
        }

        if (value instanceof CharSequence chars) {
            return normalize(chars.toString(), context);
        }

        throw new PiConversionException("Unsupported string id for " + context.fieldName() + ": " + value);
    }

    private String convertJson(JsonElement element, PiConversionContext context) {
        if (element.isJsonNull()) {
            throw new PiConversionException(context.fieldName() + " can't be null");
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString() || primitive.isNumber() || primitive.isBoolean()) {
                return normalize(primitive.getAsString(), context);
            }
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String field : idFields) {
                if (object.has(field)) {
                    return convertUnwrapped(object.get(field), context);
                }
            }
        }

        throw new PiConversionException("Unsupported string id for " + context.fieldName() + ": " + element);
    }

    private String normalize(String value, PiConversionContext context) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        if (normalized.isEmpty()) {
            throw new PiConversionException(context.fieldName() + " can't be empty");
        }
        if (normalizeEnumName) {
            normalized = normalized
                    .replace('-', '_')
                    .replace(' ', '_')
                    .toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    public static final class Builder {
        private final List<String> idFields = new ArrayList<>(List.of("id", "name"));
        private final List<Source<?>> sources = new ArrayList<>();
        private boolean normalizeEnumName;

        private Builder() {
        }

        public Builder normalizeEnumName() {
            normalizeEnumName = true;
            return this;
        }

        public Builder idFields(String... idFields) {
            this.idFields.clear();
            for (String idField : idFields) {
                String checked = Objects.requireNonNull(idField, "idField").trim();
                if (checked.isEmpty()) {
                    throw new IllegalArgumentException("idField must not be blank");
                }
                this.idFields.add(checked);
            }
            if (this.idFields.isEmpty()) {
                throw new IllegalArgumentException("at least one id field is required");
            }
            return this;
        }

        public <S> Builder source(Class<S> sourceType, Function<S, String> reader) {
            sources.add(new Source<>(sourceType, reader));
            return this;
        }

        public PiStringIdConverter build() {
            return new PiStringIdConverter(idFields, sources, normalizeEnumName);
        }
    }

    private record Source<S>(Class<S> type, Function<S, String> reader) {
        private Source {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reader, "reader");
        }

        private String tryRead(Object value) {
            return type.isInstance(value) ? reader.apply(type.cast(value)) : null;
        }
    }
}
