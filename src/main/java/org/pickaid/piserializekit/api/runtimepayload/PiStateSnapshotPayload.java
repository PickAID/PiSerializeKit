package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PiStateSnapshotPayload(
        ResourceLocation type,
        long sequence,
        PiEntityRef owner,
        ResourceLocation schema,
        CompoundTag state
) implements PiRuntimePayload {
    public PiStateSnapshotPayload {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(schema, "schema");
        state = state == null ? new CompoundTag() : state.copy();
    }

    @Override
    public CompoundTag state() {
        return state.copy();
    }
}
