package org.pickaid.piserializekit.api.packet;

public abstract class PiBidirectionalPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.BIDIRECTIONAL;
    }

    protected abstract void handle(PiPacketContext context);
}
