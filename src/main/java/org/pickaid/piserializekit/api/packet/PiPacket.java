package org.pickaid.piserializekit.api.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a gameplay packet class for generated packet binding emission.
 *
 * <p>The common path stays light: the namespace may come from package-level
 * {@link PiPacketNamespace}, the path may be inferred from the class name, and
 * the version defaults to {@code 1}. Annotated packet types must also stay
 * top-level classes because generated companions are emitted as package-level
 * types beside the declared host. Constructors used by generated decode must
 * stay accessible and must not throw checked exceptions because generated
 * bindings instantiate packets directly.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface PiPacket {
    /**
     * Full packet id override in {@code namespace:path} form.
     */
    String id() default "";

    /**
     * Namespace override when not using package-level {@link PiPacketNamespace}.
     */
    String namespace() default "";

    /**
     * Stable packet path override when inference is not sufficient.
     */
    String path() default "";

    /**
     * Declared packet schema version. Must stay {@code >= 1}.
     */
    int version() default 1;
}
