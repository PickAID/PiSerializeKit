package org.pickaid.piserializekit.api.packet;

import net.minecraft.network.FriendlyByteBuf;

public interface PiPacketCodec<T> {
    void write(FriendlyByteBuf buffer, T value);

    T read(FriendlyByteBuf buffer);
}
