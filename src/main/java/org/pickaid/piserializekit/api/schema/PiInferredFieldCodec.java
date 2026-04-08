package org.pickaid.piserializekit.api.schema;

import org.pickaid.piserializekit.api.service.PiSerializer;

/**
 * Marker provider that tells the processor to infer field serialization from the field type.
 */
public final class PiInferredFieldCodec implements PiFieldCodecProvider<Object> {
    @Override
    public PiSerializer<Object> serializer() {
        throw new UnsupportedOperationException("PiInferredFieldCodec is a marker and must never be instantiated at runtime");
    }
}
