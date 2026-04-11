package org.pickaid.piserializekit.api.schema;

import java.util.Objects;

/**
 * Predicate-like field projection used for persisted views, client views, and filtered deltas.
 */
@FunctionalInterface
public interface PiProjection {
    /**
     * Returns whether the field belongs to this projection.
     *
     * @param descriptor field descriptor
     * @return true when included
     */
    boolean includes(PiFieldDescriptor descriptor);

    /**
     * Combines this projection with another projection.
     *
     * @param other other projection
     * @return combined projection
     */
    default PiProjection and(PiProjection other) {
        Objects.requireNonNull(other, "other");
        return descriptor -> includes(descriptor) && other.includes(descriptor);
    }
}
