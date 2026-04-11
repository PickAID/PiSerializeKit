package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * Stable field metadata emitted by generated schemas and consumed by runtime helpers.
 *
 * @param key stable field key
 * @param syncScope sync visibility for client and transport projections
 * @param persist whether the field is included in persisted saves
 * @param deltaMode how delta payloads are applied
 */
public record PiFieldDescriptor(
        PiFieldKey key,
        PiSyncScope syncScope,
        boolean persist,
        PiFieldDeltaMode deltaMode
) {
    /**
     * Creates one descriptor using the default replace delta mode.
     *
     * @param key stable field key
     * @param syncScope sync visibility
     * @param persist persisted inclusion flag
     */
    public PiFieldDescriptor(PiFieldKey key, PiSyncScope syncScope, boolean persist) {
        this(key, syncScope, persist, PiFieldDeltaMode.REPLACE);
    }

    /**
     * Canonical constructor with null validation.
     */
    public PiFieldDescriptor {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(syncScope, "syncScope");
        Objects.requireNonNull(deltaMode, "deltaMode");
    }
}
