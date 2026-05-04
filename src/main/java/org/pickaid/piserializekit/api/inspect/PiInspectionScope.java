package org.pickaid.piserializekit.api.inspect;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable object verification scope collected during traversal.
 */
public final class PiInspectionScope {
    private final Set<String> visitedPaths;

    PiInspectionScope(Set<String> visitedPaths) {
        this.visitedPaths = java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(visitedPaths, "visitedPaths"))
        );
    }

    /**
     * Returns whether one exact object path was visited.
     *
     * @param path object path
     * @return true when visited
     */
    public boolean visited(String path) {
        return visitedPaths.contains(Objects.requireNonNull(path, "path"));
    }

    /**
     * Returns visited object paths.
     *
     * @return visited paths
     */
    public Set<String> visitedPaths() {
        return visitedPaths;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final LinkedHashSet<String> visitedPaths = new LinkedHashSet<>();

        void visit(PiObjectPath path) {
            visitedPaths.add(path.toString());
        }

        PiInspectionScope build() {
            return new PiInspectionScope(visitedPaths);
        }
    }
}
