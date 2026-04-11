package org.pickaid.piserializekit.api.schema;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Common field projections.
 */
public final class PiProjections {
    private static final PiProjection ALL = descriptor -> true;
    private static final PiProjection PERSISTED = PiFieldDescriptor::persist;
    private static final PiProjection CLIENT = scopes(PiSyncScope.CHUNK, PiSyncScope.TRACKING, PiSyncScope.GLOBAL);

    private PiProjections() {
    }

    /**
     * Returns a projection that includes every field.
     *
     * @return all-fields projection
     */
    public static PiProjection all() {
        return ALL;
    }

    /**
     * Returns a projection that includes only persisted fields.
     *
     * @return persisted-fields projection
     */
    public static PiProjection persisted() {
        return PERSISTED;
    }

    /**
     * Returns the default client-visible projection.
     *
     * @return client projection
     */
    public static PiProjection client() {
        return CLIENT;
    }

    /**
     * Returns a projection backed by the requested sync scopes.
     *
     * @param scopes allowed sync scopes
     * @return scope-based projection
     */
    public static PiProjection scopes(PiSyncScope... scopes) {
        Objects.requireNonNull(scopes, "scopes");
        EnumSet<PiSyncScope> allowed = EnumSet.noneOf(PiSyncScope.class);
        for (PiSyncScope scope : scopes) {
            allowed.add(Objects.requireNonNull(scope, "scope"));
        }
        return descriptor -> allowed.contains(descriptor.syncScope());
    }
}
