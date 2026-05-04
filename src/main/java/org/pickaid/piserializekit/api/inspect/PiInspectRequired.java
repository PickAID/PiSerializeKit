package org.pickaid.piserializekit.api.inspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an inspected field or record component must be non-null.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface PiInspectRequired {
    /**
     * Optional custom issue message.
     *
     * @return custom message, or blank for a generated message
     */
    String message() default "";
}
