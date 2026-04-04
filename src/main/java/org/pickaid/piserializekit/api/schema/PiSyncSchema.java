package org.pickaid.piserializekit.api.schema;

import net.minecraft.nbt.CompoundTag;

public interface PiSyncSchema<T> {
    CompoundTag saveFull(T self);

    void loadFull(T self, CompoundTag tag, PiDecodeContext context);

    CompoundTag saveClientView(T self);

    CompoundTag writeDelta(T self, PiDirtySet dirtySet);

    void applyDelta(T self, CompoundTag tag, PiDecodeContext context);
}
