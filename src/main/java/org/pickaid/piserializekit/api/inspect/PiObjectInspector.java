package org.pickaid.piserializekit.api.inspect;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Public entry point for object field caching and object graph walking.
 */
public final class PiObjectInspector {
    private static final ClassValue<PiObjectType<?>> TYPES = new ClassValue<>() {
        @Override
        protected PiObjectType<?> computeValue(Class<?> type) {
            return PiObjectType.create(type);
        }
    };

    private PiObjectInspector() {
    }

    /**
     * Returns the cached field model for one type.
     *
     * @param type inspected type
     * @param <T> inspected type
     * @return cached type model
     */
    @SuppressWarnings("unchecked")
    public static <T> PiObjectType<T> of(Class<T> type) {
        return (PiObjectType<T>) TYPES.get(Objects.requireNonNull(type, "type"));
    }

    /**
     * Walks an object graph with default options.
     *
     * @param root root object
     * @param visitor visitor receiving root and children
     */
    public static void walk(Object root, PiObjectVisitor visitor) {
        walk(root, PiObjectWalkOptions.defaults(), visitor);
    }

    /**
     * Walks an object graph.
     *
     * @param root root object
     * @param options walk options
     * @param visitor visitor receiving root and children
     */
    public static void walk(Object root, PiObjectWalkOptions options, PiObjectVisitor visitor) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(visitor, "visitor");
        new Walker(options, visitor).walk(PiObjectPath.root(), root, root == null ? Object.class : root.getClass(), null, 0);
    }

    private static final class Walker {
        private final PiObjectWalkOptions options;
        private final PiObjectVisitor visitor;
        private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();

        private Walker(PiObjectWalkOptions options, PiObjectVisitor visitor) {
            this.options = options;
            this.visitor = visitor;
        }

        private void walk(PiObjectPath path, Object value, Class<?> declaredType, PiObjectField field, int depth) {
            if (value == null) {
                if (options.includeNullValues()) {
                    visitor.visit(new PiObjectVisit(path, null, declaredType, field));
                }
                return;
            }

            visitor.visit(new PiObjectVisit(path, value, declaredType, field));
            if (depth >= options.maxDepth()) {
                return;
            }

            Class<?> valueType = value.getClass();
            if (options.isLeaf(field, valueType)) {
                return;
            }
            if (options.detectCycles() && seen.put(value, Boolean.TRUE) != null) {
                return;
            }

            if (valueType.isArray()) {
                walkArray(path, value, depth);
                return;
            }
            if (value instanceof Collection<?> collection) {
                walkCollection(path, collection, depth);
                return;
            }
            if (value instanceof Map<?, ?> map) {
                walkMap(path, map, depth);
                return;
            }
            if (valueType.isRecord()) {
                if (options.traverseRecords()) {
                    walkFields(path, value, valueType, depth);
                }
                return;
            }
            if (options.traversePlainObjects()) {
                walkFields(path, value, valueType, depth);
            }
        }

        private void walkArray(PiObjectPath path, Object array, int depth) {
            if (!options.traverseArrays()) {
                return;
            }
            int length = Array.getLength(array);
            Class<?> componentType = array.getClass().getComponentType();
            for (int index = 0; index < length; index++) {
                walk(path.index(index), Array.get(array, index), componentType, null, depth + 1);
            }
        }

        private void walkCollection(PiObjectPath path, Collection<?> collection, int depth) {
            if (!options.traverseCollections()) {
                return;
            }
            int index = 0;
            for (Object child : collection) {
                walk(path.index(index), child, Object.class, null, depth + 1);
                index++;
            }
        }

        private void walkMap(PiObjectPath path, Map<?, ?> map, int depth) {
            if (!options.traverseMaps()) {
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                walk(path.key(entry.getKey()), entry.getValue(), Object.class, null, depth + 1);
            }
        }

        private void walkFields(PiObjectPath path, Object owner, Class<?> ownerType, int depth) {
            PiObjectType<?> inspected = PiObjectInspector.of(ownerType);
            for (PiObjectField childField : inspected.fields()) {
                if (!options.includes(childField)) {
                    continue;
                }
                PiObjectPath childPath = path.field(childField.name());
                Object childValue;
                try {
                    childValue = childField.read(owner);
                } catch (PiObjectInspectionException exception) {
                    throw new PiObjectInspectionException(
                            childPath.toString(),
                            ownerType.getName(),
                            exception.getMessage(),
                            exception
                    );
                }
                walk(childPath, childValue, childField.type(), childField, depth + 1);
            }
        }
    }
}
