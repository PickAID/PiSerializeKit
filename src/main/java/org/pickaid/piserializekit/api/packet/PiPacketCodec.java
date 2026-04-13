package org.pickaid.piserializekit.api.packet;

import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;

/**
 * Packet codec used by generated packet bindings.
 *
 * @param <T> packet type
 */
public interface PiPacketCodec<T> {
    /**
     * Encodes one packet into the transport buffer.
     */
    void write(FriendlyByteBuf buffer, T value);

    /**
     * Decodes one packet while collecting structured issues into the supplied context.
     */
    T read(FriendlyByteBuf buffer, PiDecodeContext context);

    /**
     * Decodes one packet in strict mode and throws when any decode issues are reported.
     */
    default T read(FriendlyByteBuf buffer) {
        PiDecodeContext context = PiDecodeContext.strict();
        T value;
        try {
            value = read(buffer, context);
        } catch (RuntimeException exception) {
            String detail = exception.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = exception.getClass().getSimpleName();
            }
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    "",
                    "packet decode failed: " + detail,
                    true
            );
            value = null;
        }
        if (context.result().hasIssues()) {
            throw new PiPacketCodecDecodeException(context.result());
        }
        return value;
    }
}
