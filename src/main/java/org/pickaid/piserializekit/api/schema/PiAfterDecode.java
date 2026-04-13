package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-arg instance method that should run after generated decode paths
 * apply full or delta state data.
 *
 * <p>Annotated methods must not throw checked exceptions because generated
 * bindings invoke them directly.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface PiAfterDecode {
}
