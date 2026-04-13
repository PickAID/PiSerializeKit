package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a state class for generated schema, persistence, and packet bindings.
 *
 * <p>Annotated types must be top-level classes because PiSerializeKit emits
 * package-level generated companions beside the authored host type. State hosts
 * must also expose an accessible no-arg constructor that does not throw checked
 * exceptions because generated bindings instantiate them directly.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface PiSyncModel {
    /**
     * Returns the explicit schema id as a valid {@code namespace:path} resource location.
     */
    String id();

    /**
     * Returns the schema version used in serialized headers.
     */
    int version() default 1;
}
