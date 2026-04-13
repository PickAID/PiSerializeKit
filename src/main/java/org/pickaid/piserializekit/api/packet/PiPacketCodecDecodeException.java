package org.pickaid.piserializekit.api.packet;

import java.util.Objects;
import org.pickaid.piserializekit.api.schema.PiDecodeResult;

/**
 * Structured strict decode failure for standalone packet codecs without a packet id.
 */
public final class PiPacketCodecDecodeException extends IllegalStateException {
    private final PiDecodeResult result;

    public PiPacketCodecDecodeException(PiDecodeResult result) {
        super("Failed to decode Pi packet payload [" + Objects.requireNonNull(result, "result").severityLabel()
                + "]: " + result.authorSummary());
        this.result = result;
    }

    public PiDecodeResult result() {
        return result;
    }
}
