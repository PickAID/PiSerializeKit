package org.pickaid.piserializekit.api.inspect;

import org.pickaid.piserializekit.api.runtime.PiRuntimeException;

/**
 * Failure raised while inspecting object fields, reading a cached field, creating a record,
 * or walking an object graph.
 */
public final class PiObjectInspectionException extends PiRuntimeException {
    private final String path;
    private final String typeName;

    /**
     * Creates one object inspection failure.
     *
     * @param path object path involved in the failure, or an empty string when not path-specific
     * @param typeName type involved in the failure, or an empty string when unknown
     * @param message failure message
     */
    public PiObjectInspectionException(String path, String typeName, String message) {
        super("object_inspection", message);
        this.path = path == null ? "" : path;
        this.typeName = typeName == null ? "" : typeName;
    }

    /**
     * Creates one object inspection failure with a cause.
     *
     * @param path object path involved in the failure, or an empty string when not path-specific
     * @param typeName type involved in the failure, or an empty string when unknown
     * @param message failure message
     * @param cause original failure
     */
    public PiObjectInspectionException(String path, String typeName, String message, Throwable cause) {
        super("object_inspection", message, cause);
        this.path = path == null ? "" : path;
        this.typeName = typeName == null ? "" : typeName;
    }

    /**
     * Object graph path involved in the failure.
     *
     * @return path string, or an empty string
     */
    public String path() {
        return path;
    }

    /**
     * Type involved in the failure.
     *
     * @return type name, or an empty string
     */
    public String typeName() {
        return typeName;
    }
}
