package org.pickaid.piserializekit.api.packet;

/**
 * Base runtime context exposed to generated packet dispatch.
 */
public interface PiPacketContext {
    boolean clientbound();
}
