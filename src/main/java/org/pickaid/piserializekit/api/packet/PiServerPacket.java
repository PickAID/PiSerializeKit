package org.pickaid.piserializekit.api.packet;

/**
 * Base type for serverbound packets on the common author path.
 */
public abstract class PiServerPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.SERVERBOUND;
    }

    protected abstract void handle(PiServerPacketContext context);
}
