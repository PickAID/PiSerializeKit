package org.pickaid.piserializekit.api.packet;

/**
 * Base type for clientbound packets on the common author path.
 */
public abstract class PiClientPacket {
    public final PiPacketDirection direction() {
        return PiPacketDirection.CLIENTBOUND;
    }

    protected abstract void handle(PiClientPacketContext context);
}
