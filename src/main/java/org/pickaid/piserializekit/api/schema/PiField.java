package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a generated schema field and its sync and persistence behavior.
 *
 * <p>Packet classes may use the compact form and rely on defaults. Sync-model
 * fields should stay explicit so generated state schema remains intentional.
 * On packet classes, the transport-only metadata stays fixed: {@code sync=OWNER},
 * {@code persist=false}, and {@code delta=REPLACE}. Generated bindings access
 * fields directly, so annotated fields must stay non-private and non-static.
 * Annotated fields must also stay non-transient because PiSerializeKit owns the
 * persistence and transport contract directly.
 * Packet fields may still be {@code final} because generated packet decode goes
 * through the declared constructor instead of mutating an existing instance.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface PiField {
    /**
     * Returns the stable field key used in serialized payloads.
     *
     * <p>When left blank, generated bindings infer the key from the field name
     * using snake_case. Explicit keys must resolve to a stable lowercase payload
     * key and may not use the reserved {@code __pi_} prefix.</p>
     */
    String id() default "";

    /**
     * Returns the sync visibility for this field.
     *
     * <p>Packet fields must keep the default {@link PiSyncScope#OWNER} value.</p>
     */
    PiSyncScope sync() default PiSyncScope.OWNER;

    /**
     * Returns whether the field should be included in persisted saves.
     *
     * <p>Packet fields must keep the default {@code false} value.</p>
     */
    boolean persist() default false;

    /**
     * Returns the optional field-local serializer provider.
     *
     * <p>Custom providers must be concrete accessible classes with an
     * accessible no-arg constructor. That constructor must also avoid checked
     * exceptions because generated bindings instantiate providers directly.</p>
     */
    Class<? extends PiFieldCodecProvider<?>> serializer() default PiInferredFieldCodec.class;

    /**
     * Returns how full and delta payloads should be applied to the field.
     *
     * <p>Packet fields must keep the default {@link PiFieldDeltaMode#REPLACE}
     * value because packet payloads do not apply field deltas.</p>
     */
    PiFieldDeltaMode delta() default PiFieldDeltaMode.REPLACE;
}
