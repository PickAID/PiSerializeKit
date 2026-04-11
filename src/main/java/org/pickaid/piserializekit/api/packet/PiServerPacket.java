package org.pickaid.piserializekit.api.packet;

public abstract class PiServerPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.SERVERBOUND;
    }

    protected abstract void handle(PiServerPacketContext context);
}
