package org.pickaid.piserializekit.api.inspect;

import java.util.Objects;
import java.util.Optional;

/**
 * One visited value in an inspected object graph.
 */
public final class PiObjectVisit {
    private final PiObjectPath path;
    private final Object value;
    private final Class<?> declaredType;
    private final PiObjectField field;

    PiObjectVisit(PiObjectPath path, Object value, Class<?> declaredType, PiObjectField field) {
        this.path = Objects.requireNonNull(path, "path");
        this.value = value;
        this.declaredType = declaredType == null ? Object.class : declaredType;
        this.field = field;
    }

    /**
     * Returns the object path for this value.
     *
     * @return object path
     */
    public PiObjectPath path() {
        return path;
    }

    /**
     * Returns the visited value. This may be null when walk options include null values.
     *
     * @return visited value
     */
    public Object value() {
        return value;
    }

    /**
     * Returns the declared type from the field, collection, map, or root context.
     *
     * @return declared type
     */
    public Class<?> declaredType() {
        return declaredType;
    }

    /**
     * Returns the runtime value type when the value is non-null.
     *
     * @return runtime type
     */
    public Optional<Class<?>> valueType() {
        return value == null ? Optional.empty() : Optional.of(value.getClass());
    }

    /**
     * Returns the field that produced this visit when this value came from a field.
     *
     * @return source field
     */
    public Optional<PiObjectField> field() {
        return Optional.ofNullable(field);
    }
}
