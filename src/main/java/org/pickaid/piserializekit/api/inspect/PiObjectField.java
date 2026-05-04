package org.pickaid.piserializekit.api.inspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cached readable object field.
 *
 * <p>The field is made accessible once when the owning type is inspected. Callers can then
 * read it repeatedly without re-discovering reflection metadata.</p>
 */
public final class PiObjectField {
    private final Field field;
    private final String name;
    private final Class<?> type;
    private final Type genericType;
    private final Class<?> declaringType;
    private final int modifiers;
    private final boolean recordComponent;
    private final List<Annotation> annotations;

    PiObjectField(Field field, boolean recordComponent) {
        this(field, recordComponent, new Annotation[0]);
    }

    PiObjectField(Field field, boolean recordComponent, Annotation[] extraAnnotations) {
        this.field = Objects.requireNonNull(field, "field");
        this.name = field.getName();
        this.type = field.getType();
        this.genericType = field.getGenericType();
        this.declaringType = field.getDeclaringClass();
        this.modifiers = field.getModifiers();
        this.recordComponent = recordComponent;
        this.annotations = mergeAnnotations(field.getDeclaredAnnotations(), extraAnnotations);
        if (!field.trySetAccessible()) {
            throw new PiObjectInspectionException(
                    "",
                    declaringType.getName(),
                    "Cannot access field " + declaringType.getName() + "#" + name
            );
        }
    }

    /**
     * Returns the field name.
     *
     * @return field name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the raw field type.
     *
     * @return raw field type
     */
    public Class<?> type() {
        return type;
    }

    /**
     * Returns the generic field type.
     *
     * @return generic field type
     */
    public Type genericType() {
        return genericType;
    }

    /**
     * Returns the class that declares the field.
     *
     * @return declaring class
     */
    public Class<?> declaringType() {
        return declaringType;
    }

    /**
     * Returns whether this field came from a Java record component.
     *
     * @return true for record component fields
     */
    public boolean recordComponent() {
        return recordComponent;
    }

    /**
     * Returns whether this field is final.
     *
     * @return true when final
     */
    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    /**
     * Returns whether this field is transient.
     *
     * @return true when transient
     */
    public boolean isTransient() {
        return Modifier.isTransient(modifiers);
    }

    /**
     * Returns whether this field is static.
     *
     * @return true when static
     */
    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    /**
     * Returns whether this field is synthetic.
     *
     * @return true when synthetic
     */
    public boolean isSynthetic() {
        return field.isSynthetic();
    }

    /**
     * Returns declared annotations on the field.
     *
     * @return annotations
     */
    public List<Annotation> annotations() {
        return annotations;
    }

    /**
     * Finds one declared annotation on the field.
     *
     * @param annotationType annotation type
     * @param <A> annotation type
     * @return annotation if present
     */
    public <A extends Annotation> Optional<A> annotation(Class<A> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return Optional.of(annotationType.cast(annotation));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether one declared annotation is present on the field.
     *
     * @param annotationType annotation type
     * @return true when present
     */
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotation(annotationType).isPresent();
    }

    /**
     * Reads this field from an owner instance.
     *
     * @param owner owner instance
     * @return field value
     */
    public Object read(Object owner) {
        Objects.requireNonNull(owner, "owner");
        if (!declaringType.isInstance(owner)) {
            throw new PiObjectInspectionException(
                    "",
                    declaringType.getName(),
                    "Cannot read " + declaringType.getName() + "#" + name
                            + " from " + owner.getClass().getName()
            );
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new PiObjectInspectionException(
                    "",
                    declaringType.getName(),
                    "Cannot read field " + declaringType.getName() + "#" + name,
                    exception
            );
        }
    }

    @Override
    public String toString() {
        return declaringType.getName() + "#" + name + ":" + type.getName();
    }

    private static List<Annotation> mergeAnnotations(Annotation[] fieldAnnotations, Annotation[] extraAnnotations) {
        List<Annotation> merged = new ArrayList<>(Arrays.asList(fieldAnnotations));
        for (Annotation annotation : extraAnnotations) {
            boolean alreadyPresent = false;
            for (Annotation existing : merged) {
                if (existing.annotationType().equals(annotation.annotationType())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                merged.add(annotation);
            }
        }
        return List.copyOf(merged);
    }
}
