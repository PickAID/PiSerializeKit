package org.pickaid.piserializekit.api.schema;

/**
 * Structured decode issue categories.
 */
public enum PiDecodeIssueCode {
    /**
     * Unclassified decode issue.
     */
    UNKNOWN,

    /**
     * Required field payload was absent.
     */
    MISSING_FIELD_PAYLOAD,

    /**
     * Raw payload tag type did not match the expected type.
     */
    TYPE_MISMATCH,

    /**
     * Payload shape was correct but the value content was invalid.
     */
    INVALID_VALUE,

    /**
     * Payload schema id did not match the binding schema id.
     */
    SCHEMA_ID_MISMATCH,

    /**
     * Payload schema version was newer, older, or otherwise incompatible.
     */
    SCHEMA_VERSION_MISMATCH,

    /**
     * Payload did not declare a schema version header.
     */
    SCHEMA_VERSION_MISSING,

    /**
     * Schema upgrade chain failed or was incomplete.
     */
    MIGRATION_FAILURE,

    /**
     * Underlying serializer threw or reported an error.
     */
    SERIALIZER_FAILURE
}
