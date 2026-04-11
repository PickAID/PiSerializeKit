package org.pickaid.piserializekit.api.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one author-defined schema upgrade method on a {@link PiSyncModel} type.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface PiSchemaUpgrade {
    /**
     * Source version consumed by this upgrade step.
     *
     * @return source version
     */
    int from();

    /**
     * Target version produced by this upgrade step.
     *
     * @return target version
     */
    int to();
}
