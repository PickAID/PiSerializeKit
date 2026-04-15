package org.pickaid.piserializekit.api.packet;

/**
 * Base type for clientbound packets on the common path.
 */
public abstract class PiClientPacket extends PiPacketBase {
    public final PiPacketDirection direction() {
        return PiPacketDirection.CLIENTBOUND;
    }
}
