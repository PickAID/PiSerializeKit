package org.pickaid.piserializekit.api.inspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a value as inspectable but not recursively traversed.
 *
 * <p>Use this for registry objects, Minecraft runtime handles, or expensive graph roots that
 * should appear in diagnostics but should not be expanded into their internals.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface PiInspectLeaf {
}
