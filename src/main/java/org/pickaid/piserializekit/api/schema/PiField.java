package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a generated schema field and its sync and persistence behavior.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface PiField {
    /**
     * Returns the stable field key used in serialized payloads.
     */
    String id();

    /**
     * Returns the sync visibility for this field.
     */
    PiSyncScope sync();

    /**
     * Returns whether the field should be included in persisted saves.
     */
    boolean persist();

    /**
     * Returns the optional field-local serializer provider.
     */
    Class<? extends PiFieldCodecProvider<?>> serializer() default PiInferredFieldCodec.class;
}
