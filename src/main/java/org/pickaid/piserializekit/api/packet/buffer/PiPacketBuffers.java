package org.pickaid.piserializekit.api.packet.buffer;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Buffer adapters shared by PiSerializeKit packet codecs.
 */
public final class PiPacketBuffers {
    private PiPacketBuffers() {
    }

    public static PiPacketBuffer wrap(FriendlyByteBuf buffer) {
        return new PiFriendlyPacketBuffer(buffer);
    }

    public static FriendlyByteBuf unwrap(PiPacketBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return buffer.requireCapability(FriendlyByteBuf.class);
    }
}
