package org.pickaid.piserializekit.api.packet.buffer;

import org.pickaid.piserializekit.api.packet.PiPacketCodecDecodeException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;

/**
 * Packet codec contract that uses PiSerializeKit's stable packet-buffer surface.
 */
public interface PiPortablePacketCodec<T> {
    void write(PiPacketBuffer buffer, T value);

    T read(PiPacketBuffer buffer, PiDecodeContext context);

    default T read(PiPacketBuffer buffer) {
        return PiPacketCodecs.fromPortable(this).read(PiPacketBuffers.unwrap(buffer));
    }
}
