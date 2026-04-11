package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * Stable schema field key composed of binding-local index and serialized field id.
 *
 * @param index stable binding-local field index
 * @param id serialized field id used in payloads
 */
public record PiFieldKey(int index, String id) {
    /**
     * Canonical constructor with validation.
     */
    public PiFieldKey {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(id, "id");
    }
}
