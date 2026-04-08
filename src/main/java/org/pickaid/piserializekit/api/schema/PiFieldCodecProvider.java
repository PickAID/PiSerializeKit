package org.pickaid.piserializekit.api.schema;

import org.pickaid.piserializekit.api.service.PiSerializer;

/**
 * Supplies a serializer for one annotated schema field.
 *
 * @param <T> field value type
 */
public interface PiFieldCodecProvider<T> {
    /**
     * Creates the serializer used for that field.
     *
     * @return field serializer
     */
    PiSerializer<T> serializer();
}
