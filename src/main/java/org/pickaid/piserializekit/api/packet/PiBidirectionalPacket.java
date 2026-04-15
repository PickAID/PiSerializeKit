package org.pickaid.piserializekit.api.packet;

/**
 * Base type for packets that may travel on either side.
 */
public abstract class PiBidirectionalPacket extends PiPacketBase {
    public final PiPacketDirection direction() {
        return PiPacketDirection.BIDIRECTIONAL;
    }
}
