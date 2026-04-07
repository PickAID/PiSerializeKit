package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface PiField {
    String id();

    PiSyncScope sync();

    boolean persist();

    Class<? extends PiFieldCodecProvider<?>> serializer() default PiInferredFieldCodec.class;
}
