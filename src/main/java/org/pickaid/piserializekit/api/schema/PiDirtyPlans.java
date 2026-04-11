package org.pickaid.piserializekit.api.schema;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Factory helpers for binding-aware dirty projections.
 */
public final class PiDirtyPlans {
    private PiDirtyPlans() {
    }

    /**
     * Projects all dirty fields against binding order.
     *
     * @param binding schema binding
     * @param dirtySet source dirty set
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> all(PiStateBinding<T> binding, PiDirtySet dirtySet) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtySet, "dirtySet");
        return all(binding, dirtySet.toBits());
    }

    /**
     * Projects all dirty fields from bit-backed tracking against binding order.
     *
     * @param binding schema binding
     * @param dirtyBits source dirty bits
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> all(PiStateBinding<T> binding, PiDirtyBits dirtyBits) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtyBits, "dirtyBits");
        return new PiDirtyPlan<>(binding, binding.fields().stream()
                .filter(dirtyBits::contains)
                .toList());
    }

    /**
     * Projects dirty fields for the requested sync scopes in binding order.
     *
     * @param binding schema binding
     * @param dirtySet source dirty set
     * @param scopes requested scopes
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> forScopes(PiStateBinding<T> binding, PiDirtySet dirtySet, PiSyncScope... scopes) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtySet, "dirtySet");
        return forBits(binding, dirtySet.toBits(), scopes);
    }

    /**
     * Projects dirty fields from bit-backed tracking for the requested sync scopes in binding order.
     *
     * @param binding schema binding
     * @param dirtyBits source dirty bits
     * @param scopes requested scopes
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> forBits(PiStateBinding<T> binding, PiDirtyBits dirtyBits, PiSyncScope... scopes) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtyBits, "dirtyBits");
        Objects.requireNonNull(scopes, "scopes");
        if (scopes.length == 0) {
            return all(binding, dirtyBits);
        }
        EnumSet<PiSyncScope> allowed = EnumSet.noneOf(PiSyncScope.class);
        for (PiSyncScope scope : scopes) {
            allowed.add(Objects.requireNonNull(scope, "scope"));
        }
        return filter(binding, dirtyBits, allowed);
    }

    /**
     * Projects dirty fields using an arbitrary projection.
     *
     * @param binding schema binding
     * @param dirtySet source dirty set
     * @param projection requested projection
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> forProjection(PiStateBinding<T> binding, PiDirtySet dirtySet, PiProjection projection) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtySet, "dirtySet");
        return forProjection(binding, dirtySet.toBits(), projection);
    }

    /**
     * Projects dirty fields using an arbitrary projection.
     *
     * @param binding schema binding
     * @param dirtyBits source dirty bits
     * @param projection requested projection
     * @param <T> state type
     * @return ordered dirty plan
     */
    public static <T> PiDirtyPlan<T> forProjection(PiStateBinding<T> binding, PiDirtyBits dirtyBits, PiProjection projection) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dirtyBits, "dirtyBits");
        Objects.requireNonNull(projection, "projection");
        List<PiFieldDescriptor> descriptors = binding.fields().stream()
                .filter(dirtyBits::contains)
                .filter(projection::includes)
                .toList();
        return new PiDirtyPlan<>(binding, descriptors);
    }

    private static <T> PiDirtyPlan<T> filter(PiStateBinding<T> binding, PiDirtyBits dirtyBits, Set<PiSyncScope> allowed) {
        List<PiFieldDescriptor> descriptors = binding.fields().stream()
                .filter(dirtyBits::contains)
                .filter(descriptor -> allowed.contains(descriptor.syncScope()))
                .toList();
        return new PiDirtyPlan<>(binding, descriptors);
    }
}
