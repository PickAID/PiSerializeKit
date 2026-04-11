package org.pickaid.piserializekit.api.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface PiPacket {
    String id() default "";

    String namespace() default "";

    String path() default "";

    int version() default 1;
}
