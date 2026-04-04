package org.pickaid.piserializekit.api.service;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface PiSerializeService {
    <T> void register(ResourceLocation id, PiSerializer<T> serializer);

    <T> Optional<PiSerializer<T>> lookup(ResourceLocation id, Class<T> type);

    default <T> void register(PiSerializerType<T> type, PiSerializer<T> serializer) {
        register(type.id(), serializer);
    }

    default <T> Optional<PiSerializer<T>> lookup(PiSerializerType<T> type) {
        return lookup(type.id(), type.javaType());
    }
}
