package org.pickaid.piserializekit.api.packet;

public abstract class PiClientPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.CLIENTBOUND;
    }

    protected abstract void handle(PiClientPacketContext context);
}
