package org.pickaid.piserializekit.api.packet;

import net.minecraft.server.level.ServerPlayer;

/**
 * Serverbound packet dispatch context.
 */
public interface PiServerPacketContext extends PiPacketContext {
    ServerPlayer player();
}
