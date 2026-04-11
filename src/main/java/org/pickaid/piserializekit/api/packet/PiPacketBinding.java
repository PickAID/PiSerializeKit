package org.pickaid.piserializekit.api.packet;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiFieldKey;

public interface PiPacketBinding<T, C extends PiPacketContext> {
    ResourceLocation packetId();

    int version();

    PiPacketDirection direction();

    Class<T> packetType();

    List<PiFieldKey> fields();

    PiPacketCodec<T> codec();

    void dispatch(T packet, C context);
}
