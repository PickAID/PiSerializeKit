package org.pickaid.piserializekit.api.schema;

import java.util.BitSet;
import java.util.Objects;

/**
 * Bit-backed dirty tracking keyed by {@link PiFieldKey#index()}.
 */
public final class PiDirtyBits {
    private final BitSet bits = new BitSet();

    /**
     * Marks one dirty field by key.
     *
     * @param key dirty field key
     * @return this tracker
     */
    public PiDirtyBits mark(PiFieldKey key) {
        bits.set(Objects.requireNonNull(key, "key").index());
        return this;
    }

    /**
     * Marks one dirty field by descriptor.
     *
     * @param descriptor dirty field descriptor
     * @return this tracker
     */
    public PiDirtyBits mark(PiFieldDescriptor descriptor) {
        return mark(Objects.requireNonNull(descriptor, "descriptor").key());
    }

    /**
     * Returns whether the key is marked dirty.
     *
     * @param key field key
     * @return true when dirty
     */
    public boolean contains(PiFieldKey key) {
        return bits.get(Objects.requireNonNull(key, "key").index());
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
    public PiDirtyBits clear(PiFieldKey key) {
        bits.clear(Objects.requireNonNull(key, "key").index());
        return this;
    }

    /**
     * Clears one dirty descriptor.
     *
     * @param descriptor field descriptor
     * @return this tracker
     */
    public PiDirtyBits clear(PiFieldDescriptor descriptor) {
        return clear(Objects.requireNonNull(descriptor, "descriptor").key());
    }

    /**
     * Clears all dirty bits.
     *
     * @return this tracker
     */
    public PiDirtyBits clear() {
        bits.clear();
        return this;
    }

    /**
     * Returns whether no dirty bits are marked.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return bits.isEmpty();
    }

    /**
     * Projects this bit set into a key set using binding descriptors as the stable id source.
     *
     * @param descriptors field descriptors in binding order
     * @return dirty set copy
     */
    public PiDirtySet toDirtySet(Iterable<PiFieldDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        PiDirtySet dirtySet = new PiDirtySet();
        for (PiFieldDescriptor descriptor : descriptors) {
            if (contains(descriptor)) {
                dirtySet.mark(descriptor.key());
            }
        }
        return dirtySet;
    }
}
