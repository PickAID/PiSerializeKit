package org.pickaid.piserializekit.runtime.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializerType;

public final class PiSerializeRuntime implements PiSerializeService {
    private final Map<ResourceLocation, Entry<?>> serializers = new ConcurrentHashMap<>();

    @Override
    public <T> void register(PiSerializerType<T> type, PiSerializer<T> serializer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(serializer, "serializer");
        Entry<?> previous = serializers.putIfAbsent(type.id(), new Entry<>(canonicalType(type.javaType()), serializer));
        if (previous != null) {
            throw new IllegalStateException("Duplicate Pi serializer registration for " + type.id());
        }
    }

    @Override
    public <T> Optional<PiSerializer<T>> lookup(ResourceLocation id, Class<T> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Entry<?> entry = serializers.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.javaType().equals(canonicalType(type))) {
            return Optional.empty();
        }
        return Optional.of(cast(entry.serializer()));
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> canonicalType(Class<T> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return (Class<T>) Boolean.class;
        }
        if (type == byte.class) {
            return (Class<T>) Byte.class;
        }
        if (type == short.class) {
            return (Class<T>) Short.class;
        }
        if (type == int.class) {
            return (Class<T>) Integer.class;
        }
        if (type == long.class) {
            return (Class<T>) Long.class;
        }
        if (type == float.class) {
            return (Class<T>) Float.class;
        }
        if (type == double.class) {
            return (Class<T>) Double.class;
        }
        if (type == char.class) {
            return (Class<T>) Character.class;
        }
        if (type == void.class) {
            return (Class<T>) Void.class;
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    private static <T> PiSerializer<T> cast(PiSerializer<?> serializer) {
        return (PiSerializer<T>) serializer;
    }

    private record Entry<T>(Class<T> javaType, PiSerializer<T> serializer) {
        private Entry {
            Objects.requireNonNull(javaType, "javaType");
            Objects.requireNonNull(serializer, "serializer");
        }
    }
}
