package org.pickaid.piserializekit.api.schema;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PiDirtySet {
    private final Set<PiFieldKey> keys = new LinkedHashSet<>();

    public PiDirtySet mark(PiFieldKey key) {
        keys.add(Objects.requireNonNull(key, "key"));
        return this;
    }

    public boolean contains(PiFieldKey key) {
        return keys.contains(key);
    }

    public PiDirtySet clear(PiFieldKey key) {
        keys.remove(Objects.requireNonNull(key, "key"));
        return this;
    }

    public PiDirtySet clear() {
        keys.clear();
        return this;
    }

    public Set<PiFieldKey> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }
}
