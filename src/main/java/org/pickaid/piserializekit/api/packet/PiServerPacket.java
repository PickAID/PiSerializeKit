package org.pickaid.piserializekit.api.packet;

/**
 * Base type for serverbound packets on the common path.
 */
public abstract class PiServerPacket extends PiPacketBase {
    public final PiPacketDirection direction() {
        return PiPacketDirection.SERVERBOUND;
    }
}
