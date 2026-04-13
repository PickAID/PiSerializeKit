package org.pickaid.piserializekit.api.packet;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiDecodeResult;

/**
 * Structured packet decode failure with packet identity and collected issues.
 */
public final class PiPacketDecodeException extends IllegalStateException {
    private final ResourceLocation packetId;
    private final PiDecodeResult result;

    public PiPacketDecodeException(ResourceLocation packetId, PiDecodeResult result) {
        super("Failed to decode Pi packet " + Objects.requireNonNull(packetId, "packetId") + " ["
                + Objects.requireNonNull(result, "result").severityLabel() + "]: " + result.authorSummary());
        this.packetId = packetId;
        this.result = result;
    }

    public ResourceLocation packetId() {
        return packetId;
    }

    public PiDecodeResult result() {
        return result;
    }
}
