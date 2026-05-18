package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiDimensionRef(ResourceLocation id) {
    public PiDimensionRef {
        Objects.requireNonNull(id, "id");
    }

    public static PiDimensionRef of(ResourceLocation id) {
        return new PiDimensionRef(id);
    }
}
