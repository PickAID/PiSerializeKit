package org.pickaid.piserializekit.api.packet;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry view over generated packet bindings.
 */
public interface PiPacketRegistry {
    /**
     * Registers one generated binding by packet type and packet id.
     */
    <T> void register(Class<T> type, PiPacketBinding<T> binding);

    /**
     * Finds a binding by packet type.
     */
    <T> Optional<PiPacketBinding<T>> find(Class<T> type);

    /**
     * Requires a binding by packet type.
     */
    <T> PiPacketBinding<T> require(Class<T> type);

    /**
     * Finds a binding by stable packet id.
     */
    Optional<PiPacketBinding<?>> find(ResourceLocation packetId);

    /**
     * Requires a binding by stable packet id.
     */
    PiPacketBinding<?> require(ResourceLocation packetId);

    /**
     * Returns known packet ids in stable order.
     */
    default List<ResourceLocation> packetIds() {
        return List.of();
    }

    /**
     * Returns known packet types in stable order.
     */
    default List<Class<?>> packetTypes() {
        return List.of();
    }
}
