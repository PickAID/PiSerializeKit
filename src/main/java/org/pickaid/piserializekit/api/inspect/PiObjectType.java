package org.pickaid.piserializekit.api.inspect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cached field model for one Java type.
 *
 * @param <T> inspected type
 */
public final class PiObjectType<T> {
    private final Class<T> type;
    private final boolean record;
    private final List<PiObjectField> fields;
    private final Map<String, PiObjectField> fieldsByName;
    private final Constructor<T> recordConstructor;

    private PiObjectType(
            Class<T> type,
            boolean record,
            List<PiObjectField> fields,
            Constructor<T> recordConstructor
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.record = record;
        this.fields = List.copyOf(fields);
        this.fieldsByName = indexFields(fields);
        this.recordConstructor = recordConstructor;
    }

    static <T> PiObjectType<T> create(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isRecord()) {
            return createRecordType(type);
        }
        return createPlainType(type);
    }

    private static <T> PiObjectType<T> createRecordType(Class<T> type) {
        RecordComponent[] components = type.getRecordComponents();
        List<PiObjectField> fields = new ArrayList<>(components.length);
        Class<?>[] constructorTypes = new Class<?>[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            constructorTypes[index] = component.getType();
            try {
                fields.add(new PiObjectField(
                        type.getDeclaredField(component.getName()),
                        true,
                        component.getDeclaredAnnotations()
                ));
            } catch (NoSuchFieldException exception) {
                throw new PiObjectInspectionException(
                        "",
                        type.getName(),
                        "Record component " + component.getName() + " has no backing field",
                        exception
                );
            }
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(constructorTypes);
            if (!constructor.trySetAccessible()) {
                throw new PiObjectInspectionException("", type.getName(), "Cannot access record constructor");
            }
            return new PiObjectType<>(type, true, fields, constructor);
        } catch (NoSuchMethodException exception) {
            throw new PiObjectInspectionException(
                    "",
                    type.getName(),
                    "Cannot find canonical record constructor",
                    exception
            );
        }
    }

    private static <T> PiObjectType<T> createPlainType(Class<T> type) {
        List<PiObjectField> fields = new ArrayList<>();
        ArrayDeque<Class<?>> hierarchy = new ArrayDeque<>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            hierarchy.addFirst(cursor);
            cursor = cursor.getSuperclass();
        }
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                fields.add(new PiObjectField(field, false));
            }
        }
        return new PiObjectType<>(type, false, fields, null);
    }

    private static Map<String, PiObjectField> indexFields(List<PiObjectField> fields) {
        Map<String, PiObjectField> indexed = new LinkedHashMap<>();
        for (PiObjectField field : fields) {
            PiObjectField previous = indexed.putIfAbsent(field.name(), field);
            if (previous != null) {
                throw new PiObjectInspectionException(
                        "",
                        field.declaringType().getName(),
                        "Duplicate inspectable field name " + field.name()
                );
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * Returns the inspected Java type.
     *
     * @return inspected type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Returns whether the inspected type is a Java record.
     *
     * @return true for records
     */
    public boolean record() {
        return record;
    }

    /**
     * Returns cached readable fields in stable declaration order.
     *
     * <p>For records this is the canonical component order. For plain classes this is superclass
     * fields first, then subclass fields.</p>
     *
     * @return cached fields
     */
    public List<PiObjectField> fields() {
        return fields;
    }

    /**
     * Finds one cached field by name.
     *
     * @param name field name
     * @return cached field if present
     */
    public Optional<PiObjectField> field(String name) {
        return Optional.ofNullable(fieldsByName.get(Objects.requireNonNull(name, "name")));
    }

    /**
     * Requires one cached field by name.
     *
     * @param name field name
     * @return cached field
     */
    public PiObjectField requireField(String name) {
        PiObjectField field = fieldsByName.get(Objects.requireNonNull(name, "name"));
        if (field == null) {
            throw new PiObjectInspectionException(
                    "",
                    type.getName(),
                    "Missing inspectable field " + type.getName() + "#" + name
            );
        }
        return field;
    }

    /**
     * Reads all cached fields from one instance in field order.
     *
     * @param owner owner instance
     * @return field values
     */
    public List<Object> readFields(T owner) {
        Objects.requireNonNull(owner, "owner");
        List<Object> values = new ArrayList<>(fields.size());
        for (PiObjectField field : fields) {
            values.add(field.read(owner));
        }
        return List.copyOf(values);
    }

    /**
     * Reads all cached fields from one instance by field name.
     *
     * @param owner owner instance
     * @return field values by field name
     */
    public Map<String, Object> readFieldMap(T owner) {
        Objects.requireNonNull(owner, "owner");
        Map<String, Object> values = new LinkedHashMap<>();
        for (PiObjectField field : fields) {
            values.put(field.name(), field.read(owner));
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Creates one record instance through the cached canonical constructor.
     *
     * @param values constructor values in record component order
     * @return record instance
     */
    public T createRecord(Object... values) {
        if (!record || recordConstructor == null) {
            throw new PiObjectInspectionException("", type.getName(), type.getName() + " is not a record");
        }
        Object[] arguments = values == null ? new Object[0] : Arrays.copyOf(values, values.length);
        if (arguments.length != fields.size()) {
            throw new PiObjectInspectionException(
                    "",
                    type.getName(),
                    "Record constructor for " + type.getName() + " expects " + fields.size()
                            + " values but got " + arguments.length
            );
        }
        try {
            return recordConstructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new PiObjectInspectionException(
                    "",
                    type.getName(),
                    "Cannot create record " + type.getName(),
                    exception
            );
        }
    }
}
