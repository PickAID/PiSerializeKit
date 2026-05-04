package org.pickaid.piserializekit.api.inspect;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Controls how {@link PiObjectInspector#walk(Object, PiObjectWalkOptions, PiObjectVisitor)}
 * traverses an object graph.
 */
public final class PiObjectWalkOptions {
    private final int maxDepth;
    private final boolean traverseRecords;
    private final boolean traversePlainObjects;
    private final boolean traverseCollections;
    private final boolean traverseMaps;
    private final boolean traverseArrays;
    private final boolean includeNullValues;
    private final boolean detectCycles;
    private final Predicate<PiObjectField> fieldFilter;
    private final Predicate<Class<?>> leafType;

    private PiObjectWalkOptions(Builder builder) {
        this.maxDepth = builder.maxDepth;
        this.traverseRecords = builder.traverseRecords;
        this.traversePlainObjects = builder.traversePlainObjects;
        this.traverseCollections = builder.traverseCollections;
        this.traverseMaps = builder.traverseMaps;
        this.traverseArrays = builder.traverseArrays;
        this.includeNullValues = builder.includeNullValues;
        this.detectCycles = builder.detectCycles;
        this.fieldFilter = builder.fieldFilter;
        this.leafType = builder.leafType;
    }

    /**
     * Returns default walk options.
     *
     * <p>Defaults traverse records, collections, maps, and arrays. Plain Java objects are not
     * traversed unless enabled, so a Minecraft entity or level reference is treated as a leaf
     * instead of exploding into a huge runtime graph.</p>
     *
     * @return default options
     */
    public static PiObjectWalkOptions defaults() {
        return builder().build();
    }

    /**
     * Creates a walk options builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    int maxDepth() {
        return maxDepth;
    }

    boolean traverseRecords() {
        return traverseRecords;
    }

    boolean traversePlainObjects() {
        return traversePlainObjects;
    }

    boolean traverseCollections() {
        return traverseCollections;
    }

    boolean traverseMaps() {
        return traverseMaps;
    }

    boolean traverseArrays() {
        return traverseArrays;
    }

    boolean includeNullValues() {
        return includeNullValues;
    }

    boolean detectCycles() {
        return detectCycles;
    }

    boolean includes(PiObjectField field) {
        return fieldFilter.test(field);
    }

    boolean isLeaf(PiObjectField field, Class<?> type) {
        return (field != null && field.isAnnotationPresent(PiInspectLeaf.class)) || leafType.test(type);
    }

    private static boolean defaultLeafType(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Class.class == type
                || type.isAnnotationPresent(PiInspectLeaf.class);
    }

    /**
     * Builder for object walk options.
     */
    public static final class Builder {
        private int maxDepth = 32;
        private boolean traverseRecords = true;
        private boolean traversePlainObjects;
        private boolean traverseCollections = true;
        private boolean traverseMaps = true;
        private boolean traverseArrays = true;
        private boolean includeNullValues = true;
        private boolean detectCycles = true;
        private Predicate<PiObjectField> fieldFilter = field ->
                !field.isStatic()
                        && !field.isSynthetic()
                        && !field.isAnnotationPresent(PiInspectIgnore.class);
        private Predicate<Class<?>> leafType = PiObjectWalkOptions::defaultLeafType;

        private Builder() {
        }

        /**
         * Sets the maximum recursive depth after the root visit.
         *
         * @param maxDepth maximum depth, {@code 0} means root only
         * @return this builder
         */
        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be >= 0");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Enables or disables traversal into Java records.
         *
         * @param traverseRecords whether records are traversed
         * @return this builder
         */
        public Builder traverseRecords(boolean traverseRecords) {
            this.traverseRecords = traverseRecords;
            return this;
        }

        /**
         * Enables or disables traversal into plain Java objects.
         *
         * @param traversePlainObjects whether plain objects are traversed
         * @return this builder
         */
        public Builder traversePlainObjects(boolean traversePlainObjects) {
            this.traversePlainObjects = traversePlainObjects;
            return this;
        }

        /**
         * Enables or disables traversal into collections.
         *
         * @param traverseCollections whether collections are traversed
         * @return this builder
         */
        public Builder traverseCollections(boolean traverseCollections) {
            this.traverseCollections = traverseCollections;
            return this;
        }

        /**
         * Enables or disables traversal into maps.
         *
         * @param traverseMaps whether map values are traversed
         * @return this builder
         */
        public Builder traverseMaps(boolean traverseMaps) {
            this.traverseMaps = traverseMaps;
            return this;
        }

        /**
         * Enables or disables traversal into arrays.
         *
         * @param traverseArrays whether arrays are traversed
         * @return this builder
         */
        public Builder traverseArrays(boolean traverseArrays) {
            this.traverseArrays = traverseArrays;
            return this;
        }

        /**
         * Sets whether null values should be emitted to the visitor.
         *
         * @param includeNullValues whether null values are visited
         * @return this builder
         */
        public Builder includeNullValues(boolean includeNullValues) {
            this.includeNullValues = includeNullValues;
            return this;
        }

        /**
         * Sets whether already-seen object identities should stop recursive traversal.
         *
         * @param detectCycles whether cycles are detected
         * @return this builder
         */
        public Builder detectCycles(boolean detectCycles) {
            this.detectCycles = detectCycles;
            return this;
        }

        /**
         * Sets the field filter used before reading a field.
         *
         * @param fieldFilter field filter
         * @return this builder
         */
        public Builder fieldFilter(Predicate<PiObjectField> fieldFilter) {
            this.fieldFilter = Objects.requireNonNull(fieldFilter, "fieldFilter");
            return this;
        }

        /**
         * Sets the leaf type predicate. Returning true means the value is visited but not traversed.
         *
         * @param leafType leaf type predicate
         * @return this builder
         */
        public Builder leafType(Predicate<Class<?>> leafType) {
            this.leafType = Objects.requireNonNull(leafType, "leafType");
            return this;
        }

        /**
         * Adds one extra leaf type on top of the current leaf predicate.
         *
         * @param leafType leaf type
         * @return this builder
         */
        public Builder leafType(Class<?> leafType) {
            Objects.requireNonNull(leafType, "leafType");
            Predicate<Class<?>> current = this.leafType;
            this.leafType = type -> current.test(type) || leafType.isAssignableFrom(type);
            return this;
        }

        /**
         * Builds immutable walk options.
         *
         * @return walk options
         */
        public PiObjectWalkOptions build() {
            return new PiObjectWalkOptions(this);
        }
    }
}
