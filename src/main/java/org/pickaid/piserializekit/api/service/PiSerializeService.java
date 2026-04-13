package org.pickaid.piserializekit.api.service;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;

public interface PiSerializeService {
    <T> void register(PiSerializerType<T> type, PiSerializer<T> serializer);

    <T> Optional<PiSerializer<T>> lookup(ResourceLocation id, Class<T> type);

    default <T> void register(ResourceLocation id, Class<T> javaType, PiSerializer<T> serializer) {
        register(new PiSerializerType<>(id, javaType), serializer);
    }

    default <T> Optional<PiSerializer<T>> lookup(PiSerializerType<T> type) {
        return lookup(type.id(), type.javaType());
    }

    /**
     * Resolves one serializer by raw id and java type or fails with an author-facing diagnostic.
     */
    default <T> PiSerializer<T> require(ResourceLocation id, Class<T> javaType) {
        return require(new PiSerializerType<>(id, javaType));
    }

    /**
     * Resolves one serializer by typed serializer id or fails with an author-facing diagnostic.
     */
    default <T> PiSerializer<T> require(PiSerializerType<T> type) {
        return lookup(type).orElseThrow(() -> new PiRuntimeLookupException(
                "serializer-id",
                type.id().toString(),
                describeMissingSerializer(type)
        ));
    }

    /**
     * Describes a missing serializer in author-facing terms.
     */
    default String describeMissingSerializer(PiSerializerType<?> type) {
        return "Missing Pi serializer for " + type.id() + " / " + type.javaType().getName();
    }

    /**
     * Returns known serializer ids in stable order for diagnostics and tooling.
     */
    default List<ResourceLocation> serializerIds() {
        return List.of();
    }

    /**
     * Returns known serializer java types in stable order for diagnostics and tooling.
     */
    default List<Class<?>> serializerJavaTypes() {
        return List.of();
    }
}
