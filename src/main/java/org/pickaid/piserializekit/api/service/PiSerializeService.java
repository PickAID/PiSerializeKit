package org.pickaid.piserializekit.api.service;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface PiSerializeService {
    <T> void register(PiSerializerType<T> type, PiSerializer<T> serializer);

    <T> Optional<PiSerializer<T>> lookup(ResourceLocation id, Class<T> type);

    default <T> void register(ResourceLocation id, Class<T> javaType, PiSerializer<T> serializer) {
        register(new PiSerializerType<>(id, javaType), serializer);
    }

    default <T> Optional<PiSerializer<T>> lookup(PiSerializerType<T> type) {
        return lookup(type.id(), type.javaType());
    }
}
