package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiInputIntentPayload(
        ResourceLocation type,
        long sequence,
        PiEntityRef actor,
        ResourceLocation intent,
        CompoundTag data
) implements PiRuntimePayload {
    public PiInputIntentPayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(intent, "intent");
        data = data == null ? new CompoundTag() : data.copy();
    }

    @Override
    public CompoundTag data() {
        return data.copy();
    }
}
