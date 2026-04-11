package org.pickaid.piserializekit.api.schema;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Structured decode failure with schema identity and collected issues.
 */
public final class PiDecodeException extends IllegalStateException {
    private final ResourceLocation schemaId;
    private final PiDecodeResult result;

    /**
     * Creates one decode failure.
     *
     * @param schemaId schema identity
     * @param result collected decode result
     */
    public PiDecodeException(ResourceLocation schemaId, PiDecodeResult result) {
        super("Failed to decode Pi schema " + Objects.requireNonNull(schemaId, "schemaId") + ": " + Objects.requireNonNull(result, "result").summary());
        this.schemaId = schemaId;
        this.result = result;
    }

    /**
     * Returns the failing schema identity.
     *
     * @return schema id
     */
    public ResourceLocation schemaId() {
        return schemaId;
    }

    /**
     * Returns the collected decode issues.
     *
     * @return decode result
     */
    public PiDecodeResult result() {
        return result;
    }
}
