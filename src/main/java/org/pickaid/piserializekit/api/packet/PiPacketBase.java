package org.pickaid.piserializekit.api.packet;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.runtime.packet.PiPackets;

abstract class PiPacketBase {
    public final ResourceLocation packetId() {
        return PiPackets.packetId(getClass());
    }

    public final int version() {
        return PiPackets.version(getClass());
    }

    public final void write(FriendlyByteBuf buffer) {
        PiPackets.write(Objects.requireNonNull(buffer, "buffer"), this);
    }

    public final void write(PiPacketBuffer buffer) {
        PiPackets.write(Objects.requireNonNull(buffer, "buffer"), this);
    }
}
