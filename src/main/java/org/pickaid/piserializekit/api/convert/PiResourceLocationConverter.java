package org.pickaid.piserializekit.api.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

public final class PiResourceLocationConverter implements PiValueConverter<ResourceLocation> {
    private final String defaultNamespace;
    private final String pathPrefix;
    private final List<String> idFields;
    private final List<Source<?>> sources;

    private PiResourceLocationConverter(String defaultNamespace, String pathPrefix, List<String> idFields, List<Source<?>> sources) {
        this.defaultNamespace = checkNamespace(defaultNamespace);
        this.pathPrefix = normalizePathPrefix(pathPrefix);
        this.idFields = List.copyOf(idFields);
        this.sources = List.copyOf(sources);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ResourceLocation convert(Object value, PiConversionContext context) {
        Objects.requireNonNull(context, "context");
        return convertUnwrapped(context.unwrap(value), context);
    }

    private ResourceLocation convertUnwrapped(Object value, PiConversionContext context) {
        if (value == null) {
            throw new PiConversionException(context.fieldName() + " can't be null");
        }

        if (value instanceof JsonElement element) {
            return convertJson(element, context);
        }

        if (value instanceof ResourceLocation location) {
            return normalize(location);
        }

        if (value instanceof RegistryObject<?> registryObject) {
            return normalize(registryObject.getId());
        }

        for (Source<?> source : sources) {
            ResourceLocation location = source.tryRead(value);
            if (location != null) {
                return normalize(location);
            }
        }

        if (value instanceof CharSequence chars) {
            return parseText(chars.toString(), context);
        }

        throw new PiConversionException("Unsupported resource location for " + context.fieldName() + ": " + value);
    }

    private ResourceLocation convertJson(JsonElement element, PiConversionContext context) {
        if (element.isJsonNull()) {
            throw new PiConversionException(context.fieldName() + " can't be null");
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString() || primitive.isNumber() || primitive.isBoolean()) {
                return parseText(primitive.getAsString(), context);
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

        throw new PiConversionException("Unsupported resource location for " + context.fieldName() + ": " + element);
    }

    public ResourceLocation normalize(ResourceLocation value) {
        Objects.requireNonNull(value, "value");
        String path = value.getPath();
        if (!pathPrefix.isEmpty() && !path.startsWith(pathPrefix + "/")) {
            path = pathPrefix + "/" + path;
        }
        return new ResourceLocation(value.getNamespace(), path);
    }

    private ResourceLocation parseText(String value, PiConversionContext context) {
        String raw = Objects.requireNonNull(value, "value").trim();
        if (raw.isEmpty()) {
            throw new PiConversionException(context.fieldName() + " can't be empty");
        }

        String normalized = raw.contains(":") ? raw : defaultNamespace + ":" + raw;
        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (parsed == null) {
            throw new PiConversionException("Invalid resource location for " + context.fieldName() + ": " + raw);
        }
        return normalize(parsed);
    }

    private static String checkNamespace(String namespace) {
        String checked = Objects.requireNonNull(namespace, "namespace").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return checked;
    }

    private static String normalizePathPrefix(String pathPrefix) {
        String checked = pathPrefix == null ? "" : pathPrefix.trim();
        while (checked.startsWith("/")) {
            checked = checked.substring(1);
        }
        while (checked.endsWith("/")) {
            checked = checked.substring(0, checked.length() - 1);
        }
        if (checked.contains("..")) {
            throw new IllegalArgumentException("pathPrefix must not contain '..'");
        }
        return checked;
    }

    public static final class Builder {
        private String defaultNamespace = "minecraft";
        private String pathPrefix = "";
        private final List<String> idFields = new ArrayList<>(List.of("id", "name"));
        private final List<Source<?>> sources = new ArrayList<>();

        private Builder() {
        }

        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = checkNamespace(defaultNamespace);
            return this;
        }

        public Builder pathPrefix(String pathPrefix) {
            this.pathPrefix = normalizePathPrefix(pathPrefix);
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

        public <S> Builder source(Class<S> sourceType, Function<S, ResourceLocation> reader) {
            sources.add(new Source<>(sourceType, reader));
            return this;
        }

        public PiResourceLocationConverter build() {
            return new PiResourceLocationConverter(defaultNamespace, pathPrefix, idFields, sources);
        }
    }

    private record Source<S>(Class<S> type, Function<S, ResourceLocation> reader) {
        private Source {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reader, "reader");
        }

        private ResourceLocation tryRead(Object value) {
            return type.isInstance(value) ? reader.apply(type.cast(value)) : null;
        }
    }
}
