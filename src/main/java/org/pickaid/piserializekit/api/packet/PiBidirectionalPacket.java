package org.pickaid.piserializekit.api.packet;

/**
 * Base type for packets that may dispatch on either side.
 */
public abstract class PiBidirectionalPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.BIDIRECTIONAL;
    }

    protected abstract void handle(PiPacketContext context);
}
