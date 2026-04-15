package org.pickaid.piserializekit.api.service;

import com.mojang.serialization.Codec;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketCodecs;
import org.pickaid.piserializekit.api.packet.buffer.PiPortablePacketCodec;

public interface PiSerializer<T> {
    Codec<T> valueCodec();

    PiNbtCodec<T> nbtCodec();

    PiPacketCodec<T> packetCodec();

    default PiPortablePacketCodec<T> portablePacketCodec() {
        return PiPacketCodecs.portable(packetCodec());
    }
}
