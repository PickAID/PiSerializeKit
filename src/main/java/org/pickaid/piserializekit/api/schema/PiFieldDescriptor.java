package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

public record PiFieldDescriptor(
        PiFieldKey key,
        PiSyncScope syncScope,
        boolean persist
) {
    public PiFieldDescriptor {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(syncScope, "syncScope");
    }
}
