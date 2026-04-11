package org.pickaid.piserializekit.api.schema;

/**
 * Decode payload kind for schema migration and normalization.
 */
public enum PiSchemaPayloadKind {
    /**
     * A full state snapshot.
     */
    FULL,

    /**
     * A persisted state snapshot.
     */
    PERSISTED,

    /**
     * A partial delta payload.
     */
    DELTA
}
