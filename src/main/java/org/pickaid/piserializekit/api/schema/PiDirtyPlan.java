package org.pickaid.piserializekit.api.schema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Scope-aware dirty projection for one binding.
 *
 * @param <T> state type
 */
public final class PiDirtyPlan<T> {
    private final PiStateBinding<T> binding;
    private final List<PiFieldDescriptor> descriptors;

    PiDirtyPlan(PiStateBinding<T> binding, List<PiFieldDescriptor> descriptors) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
    }

    /**
     * Returns whether no fields remain in this plan.
     *
     * @return true when no dirty fields match
     */
    public boolean isEmpty() {
        return descriptors.isEmpty();
    }

    /**
     * Returns the ordered dirty descriptors selected for this plan.
     *
     * @return ordered dirty descriptors
     */
    public List<PiFieldDescriptor> descriptors() {
        return descriptors;
    }

    /**
     * Returns the selected dirty keys in binding order.
     *
     * @return selected dirty keys
     */
    public Set<PiFieldKey> keys() {
        LinkedHashSet<PiFieldKey> keys = new LinkedHashSet<>();
        for (PiFieldDescriptor descriptor : descriptors) {
            keys.add(descriptor.key());
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Writes a delta using only the selected dirty keys.
     *
     * @param self state to serialize
     * @return delta payload
     */
    public CompoundTag writeDelta(T self) {
        PiDirtySet dirtySet = new PiDirtySet();
        for (PiFieldDescriptor descriptor : descriptors) {
            dirtySet.mark(descriptor.key());
        }
        return binding.writeDelta(self, dirtySet);
    }
}
