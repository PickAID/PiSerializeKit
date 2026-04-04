package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

public record PiFieldKey(int index, String id) {
    public PiFieldKey {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(id, "id");
    }
}
