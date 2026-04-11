package org.pickaid.piserializekit.api.packet;

import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;

public interface PiPacketCodec<T> {
    void write(FriendlyByteBuf buffer, T value);

    T read(FriendlyByteBuf buffer, PiDecodeContext context);

    default T read(FriendlyByteBuf buffer) {
        PiDecodeContext context = PiDecodeContext.strict();
        T value = read(buffer, context);
        if (context.result().hasIssues()) {
            throw new IllegalStateException("Failed to decode Pi packet payload: " + context.result().summary());
        }
        return value;
    }
}
