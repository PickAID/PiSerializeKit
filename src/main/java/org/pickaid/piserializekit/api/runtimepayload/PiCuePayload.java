package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiCuePayload(
        ResourceLocation type,
        long sequence,
        PiEntityRef source,
        CompoundTag data
) implements PiRuntimePayload {
    public PiCuePayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        data = data == null ? new CompoundTag() : data.copy();
    }

    @Override
    public CompoundTag data() {
        return data.copy();
    }

    public static PiCuePayload of(ResourceLocation type, long sequence, PiEntityRef source) {
        return new PiCuePayload(type, sequence, source, new CompoundTag());
    }
}
