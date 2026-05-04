package org.pickaid.piserializekit.api.inspect;

/**
 * Structured object inspection issue categories.
 */
public enum PiInspectionIssueCode {
    /**
     * Unclassified object inspection issue.
     */
    UNKNOWN,

    /**
     * A required value was null.
     */
    NULL_VALUE,

    /**
     * A value had the wrong runtime type.
     */
    TYPE_MISMATCH,

    /**
     * A value was present but failed a domain rule.
     */
    INVALID_VALUE,

    /**
     * A field required by a higher-level rule was absent.
     */
    MISSING_FIELD,

    /**
     * The object graph exceeded a required structural limit.
     */
    STRUCTURE_LIMIT,

    /**
     * A reflective read, constructor call, or traversal step failed.
     */
    INSPECTION_FAILURE
}
