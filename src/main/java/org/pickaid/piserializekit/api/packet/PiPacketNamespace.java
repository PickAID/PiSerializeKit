package org.pickaid.piserializekit.api.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the default namespace for all {@link PiPacket} types in one package.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PACKAGE)
public @interface PiPacketNamespace {
    String value();
}
