package org.pickaid.piserializekit.api.nbt;

import net.minecraft.nbt.CompoundTag;

public interface PiNbtCodec<T> {
    CompoundTag encode(T value);

    T decode(CompoundTag tag);
}
