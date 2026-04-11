package org.pickaid.piserializekit.api.packet;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface PiPacketRegistry {
    <T> void register(Class<T> type, PiPacketBinding<T, ?> binding);

    <T> Optional<PiPacketBinding<T, ?>> find(Class<T> type);

    <T> PiPacketBinding<T, ?> require(Class<T> type);

    Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId);

    PiPacketBinding<?, ?> require(ResourceLocation packetId);
}
