package org.pickaid.piserializekit.runtime.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializerType;

public final class PiSerializeRuntime implements PiSerializeService {
    private final Map<ResourceLocation, Entry<?>> serializers = new ConcurrentHashMap<>();

    @Override
    public <T> void register(PiSerializerType<T> type, PiSerializer<T> serializer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(serializer, "serializer");
        Class<T> canonicalJavaType = canonicalType(type.javaType());
        Entry<T> incoming = new Entry<>(canonicalJavaType, serializer);
        Entry<?> previous = serializers.putIfAbsent(type.id(), incoming);
        if (previous == null) {
            return;
        }
        if (previous.javaType().equals(canonicalJavaType) && previous.serializer() == serializer) {
            return;
        }
        if (previous.javaType().equals(canonicalJavaType)) {
            throw new PiRuntimeConflictException(
                    "serializer-id",
                    type.id().toString(),
                    "Duplicate Pi serializer registration for " + type.id()
                            + "; java type " + canonicalJavaType.getName()
                            + " is already registered. Use PiSerializeServices.withScope(...) for overrides instead of re-registering the same id and type."
            );
        }
        throw new PiRuntimeConflictException(
                "serializer-id",
                type.id().toString(),
                "Duplicate Pi serializer registration for " + type.id()
                        + "; existing java type " + previous.javaType().getName()
                        + ", conflicting java type " + canonicalJavaType.getName()
        );
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

    @Override
    public String describeMissingSerializer(PiSerializerType<?> type) {
        Objects.requireNonNull(type, "type");
        Entry<?> entry = serializers.get(type.id());
        if (entry != null) {
            return "Missing Pi serializer for " + type.id() + " / " + type.javaType().getName()
                    + "; serializer id is registered with java type " + entry.javaType().getName();
        }
        return "Missing Pi serializer for " + type.id() + " / " + type.javaType().getName()
                + "; known serializer ids: " + describeKnownSerializerIds()
                + emptyRuntimeHint();
    }

    @Override
    public List<ResourceLocation> serializerIds() {
        ArrayList<ResourceLocation> ids = new ArrayList<>(serializers.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(ids);
    }

    @Override
    public List<Class<?>> serializerJavaTypes() {
        ArrayList<Class<?>> types = new ArrayList<>(serializers.size());
        for (Entry<?> entry : serializers.values()) {
            types.add(entry.javaType());
        }
        types.sort(Comparator.comparing(Class::getName));
        ArrayList<Class<?>> deduped = new ArrayList<>(types.size());
        Class<?> previous = null;
        for (Class<?> type : types) {
            if (!type.equals(previous)) {
                deduped.add(type);
                previous = type;
            }
        }
        return List.copyOf(deduped);
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

    private String describeKnownSerializerIds() {
        if (serializers.isEmpty()) {
            return "<none>";
        }
        ArrayList<String> ids = new ArrayList<>(serializers.size());
        for (ResourceLocation id : serializers.keySet()) {
            ids.add(id.toString());
        }
        ids.sort(String::compareTo);
        int limit = Math.min(6, ids.size());
        String joined = String.join(", ", ids.subList(0, limit));
        if (ids.size() > limit) {
            return joined + ", +" + (ids.size() - limit) + " more";
        }
        return joined;
    }

    private String emptyRuntimeHint() {
        if (!serializers.isEmpty()) {
            return "";
        }
        return "; serializer runtime is empty. Install built-ins or register serializers before resolving author-facing codec ids.";
    }

    private record Entry<T>(Class<T> javaType, PiSerializer<T> serializer) {
        private Entry {
            Objects.requireNonNull(javaType, "javaType");
            Objects.requireNonNull(serializer, "serializer");
        }
    }
}
