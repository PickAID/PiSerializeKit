package org.pickaid.piserializekit.api.schema;

/**
 * Field-level apply strategy for full and delta payloads.
 */
public enum PiFieldDeltaMode {
    /**
     * Replace the current field value with the decoded payload.
     */
    REPLACE,

    /**
     * For nested {@link PiSyncModel} values, decode into the current instance when possible.
     */
    NESTED_UPDATE,

    /**
     * For set fields, merge delta payload entries into the existing set instead of replacing it.
     */
    MERGE_SET,

    /**
     * For map fields, merge delta payload entries into the existing map instead of replacing it.
     */
    MERGE_MAP
}
