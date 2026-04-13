package org.pickaid.piserializekit.api.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one packet payload migration step from {@code from()} to {@code to()}.
 *
 * <p>The annotated method is consumed by generated packet bindings and must
 * satisfy the packet-upgrade processor contract.</p>
 *
 * <p>Expected method shape:
 * {@code static CompoundTag method(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context)}.
 * Annotated methods must not throw checked exceptions because generated
 * migrations invoke them directly.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface PiPacketUpgrade {
    /**
     * Source packet version handled by the annotated migration step.
     */
    int from();

    /**
     * Target packet version produced by the annotated migration step.
     */
    int to();
}
