package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiRegistryIdRef(ResourceLocation registry, ResourceLocation id) {
    public PiRegistryIdRef {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(id, "id");
    }

    public static PiRegistryIdRef of(ResourceLocation registry, ResourceLocation id) {
        return new PiRegistryIdRef(registry, id);
    }
}
