package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiSignalPayload(
        ResourceLocation type,
        long sequence,
        PiEntityRef source,
        PiEntityRef target,
        CompoundTag payload
) implements PiRuntimePayload {
    public PiSignalPayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        payload = payload == null ? new CompoundTag() : payload.copy();
    }

    @Override
    public CompoundTag payload() {
        return payload.copy();
    }
}
