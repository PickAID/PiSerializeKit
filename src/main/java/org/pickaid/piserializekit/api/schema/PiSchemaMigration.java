package org.pickaid.piserializekit.api.schema;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * One binding-local schema upgrade step.
 *
 * @param fromVersion source schema version
 * @param toVersion target schema version
 * @param function migration body
 */
public record PiSchemaMigration(
        int fromVersion,
        int toVersion,
        PiSchemaMigrationFunction function
) {
    /**
     * Creates one schema migration step.
     *
     * @param fromVersion source schema version
     * @param toVersion target schema version
     * @param function migration body
     * @return migration step
     */
    public static PiSchemaMigration step(int fromVersion, int toVersion, PiSchemaMigrationFunction function) {
        return new PiSchemaMigration(fromVersion, toVersion, function);
    }

    /**
     * Canonical constructor with validation.
     *
     * @param fromVersion source schema version
     * @param toVersion target schema version
     * @param function migration body
     */
    public PiSchemaMigration {
        if (fromVersion < 1) {
            throw new IllegalArgumentException("Schema migration source version must be >= 1");
        }
        if (toVersion <= fromVersion) {
            throw new IllegalArgumentException("Schema migration target version must be greater than source version");
        }
        Objects.requireNonNull(function, "function");
    }

    /**
     * Applies this migration step.
     *
     * @param payload payload to migrate
     * @param kind payload kind
     * @param context decode context for diagnostics
     * @return migrated payload
     */
    public CompoundTag apply(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {
        return function.migrate(payload, kind, context);
    }
}
