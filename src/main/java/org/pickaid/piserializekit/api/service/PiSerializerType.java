package org.pickaid.piserializekit.api.service;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record PiSerializerType<T>(ResourceLocation id, Class<T> javaType) {
    public PiSerializerType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(javaType, "javaType");
    }
}
