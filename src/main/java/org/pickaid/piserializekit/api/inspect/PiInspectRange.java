package org.pickaid.piserializekit.api.inspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an inclusive numeric range for object inspection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface PiInspectRange {
    /**
     * Inclusive minimum.
     *
     * @return minimum value
     */
    double min() default -Double.MAX_VALUE;

    /**
     * Inclusive maximum.
     *
     * @return maximum value
     */
    double max() default Double.MAX_VALUE;

    /**
     * Optional custom issue message.
     *
     * @return custom message, or blank for a generated message
     */
    String message() default "";
}
