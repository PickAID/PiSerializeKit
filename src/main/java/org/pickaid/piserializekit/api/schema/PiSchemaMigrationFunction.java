package org.pickaid.piserializekit.api.schema;

import net.minecraft.nbt.CompoundTag;

/**
 * Migration step body for one schema-version upgrade.
 */
@FunctionalInterface
public interface PiSchemaMigrationFunction {
    /**
     * Migrates one payload from an older schema version to a newer one.
     *
     * @param payload payload to migrate
     * @param kind payload kind
     * @param context decode context for diagnostics
     * @return migrated payload
     */
    CompoundTag migrate(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context);
}
