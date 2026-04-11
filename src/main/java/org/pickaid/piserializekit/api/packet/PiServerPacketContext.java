package org.pickaid.piserializekit.api.packet;

import net.minecraft.server.level.ServerPlayer;

public interface PiServerPacketContext extends PiPacketContext {
    ServerPlayer player();
}
