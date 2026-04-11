package org.pickaid.piserializekit.api.schema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Key-backed dirty tracking for host code and author-owned runtime logic.
 */
public final class PiDirtySet {
    private final Set<PiFieldKey> keys = new LinkedHashSet<>();

    /**
     * Marks one dirty field by key.
     *
     * @param key dirty field key
     * @return this tracker
     */
    public PiDirtySet mark(PiFieldKey key) {
        keys.add(Objects.requireNonNull(key, "key"));
        return this;
    }

    /**
     * Marks one dirty field by descriptor.
     *
     * @param descriptor dirty field descriptor
     * @return this tracker
     */
    public PiDirtySet mark(PiFieldDescriptor descriptor) {
        return mark(Objects.requireNonNull(descriptor, "descriptor").key());
    }

    /**
     * Returns whether the key is marked dirty.
     *
     * @param key field key
     * @return true when dirty
     */
    public boolean contains(PiFieldKey key) {
        return keys.contains(Objects.requireNonNull(key, "key"));
    }

    /**
     * Returns whether the descriptor is marked dirty.
     *
     * @param descriptor field descriptor
     * @return true when dirty
     */
    public boolean contains(PiFieldDescriptor descriptor) {
        return contains(Objects.requireNonNull(descriptor, "descriptor").key());
    }

    /**
     * Clears one dirty key.
     *
     * @param key field key
     * @return this tracker
     */
    public PiDirtySet clear(PiFieldKey key) {
        keys.remove(Objects.requireNonNull(key, "key"));
        return this;
    }

    /**
     * Clears one dirty descriptor.
     *
     * @param descriptor field descriptor
     * @return this tracker
     */
    public PiDirtySet clear(PiFieldDescriptor descriptor) {
        return clear(Objects.requireNonNull(descriptor, "descriptor").key());
    }

    /**
     * Clears all dirty keys.
     *
     * @return this tracker
     */
    public PiDirtySet clear() {
        keys.clear();
        return this;
    }

    /**
     * Returns the dirty keys in stable insertion order.
     *
     * @return unmodifiable dirty key view
     */
    public Set<PiFieldKey> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    /**
     * Projects this set into bit-backed dirty tracking keyed by field index.
     *
     * @return dirty bits copy
     */
    public PiDirtyBits toBits() {
        PiDirtyBits bits = new PiDirtyBits();
        for (PiFieldKey key : keys) {
            bits.mark(key);
        }
        return bits;
    }
}
