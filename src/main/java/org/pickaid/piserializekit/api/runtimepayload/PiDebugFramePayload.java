package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiDebugFramePayload(
        ResourceLocation type,
        long sequence,
        ResourceLocation channel,
        CompoundTag frame
) implements PiRuntimePayload {
    public PiDebugFramePayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        frame = frame == null ? new CompoundTag() : frame.copy();
    }

    @Override
    public CompoundTag frame() {
        return frame.copy();
    }
}
