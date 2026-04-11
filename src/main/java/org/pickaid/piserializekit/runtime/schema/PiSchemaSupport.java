package org.pickaid.piserializekit.runtime.schema;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiStateBinding;

public final class PiSchemaSupport {
    public static final String SCHEMA_ID_KEY = "__pi_schema";
    public static final String SCHEMA_VERSION_KEY = "__pi_version";

    private PiSchemaSupport() {
    }

    @SafeVarargs
    public static CompoundTag tagOf(Pair<String, Tag>... entries) {
        CompoundTag tag = new CompoundTag();
        for (Pair<String, Tag> entry : entries) {
            tag.put(entry.getFirst(), entry.getSecond());
        }
        return tag;
    }

    @SafeVarargs
    public static CompoundTag tagWithHeader(String schemaId, int version, Pair<String, Tag>... entries) {
        CompoundTag tag = headerTag(schemaId, version);
        for (Pair<String, Tag> entry : entries) {
            tag.put(entry.getFirst(), entry.getSecond());
        }
        return tag;
    }

    public static CompoundTag headerTag(String schemaId, int version) {
        CompoundTag tag = new CompoundTag();
        tag.putString(SCHEMA_ID_KEY, schemaId);
        tag.putInt(SCHEMA_VERSION_KEY, version);
        return tag;
    }

    @SafeVarargs
    public static CompoundTag tagWithHeader(ResourceLocation schemaId, int version, Pair<String, Tag>... entries) {
        Objects.requireNonNull(schemaId, "schemaId");
        return tagWithHeader(schemaId.toString(), version, entries);
    }

    public static CompoundTag headerTag(ResourceLocation schemaId, int version) {
        Objects.requireNonNull(schemaId, "schemaId");
        return headerTag(schemaId.toString(), version);
    }

    public static boolean validateHeader(CompoundTag tag, PiDecodeContext context, String expectedSchemaId, int expectedVersion) {
        boolean valid = true;
        if (!validateSchemaId(tag, context, expectedSchemaId)) {
            valid = false;
        }
        Integer version = readSchemaVersion(tag, context);
        if (version == null) {
            valid = false;
        } else if (expectedVersion != version) {
            context.issue(
                    PiDecodeIssueCode.SCHEMA_VERSION_MISMATCH,
                    SCHEMA_VERSION_KEY,
                    "expected schema version " + expectedVersion + " but got " + tag.getInt(SCHEMA_VERSION_KEY),
                    true
            );
            valid = false;
        }
        return valid;
    }

    public static boolean validateHeader(CompoundTag tag, PiDecodeContext context, ResourceLocation expectedSchemaId, int expectedVersion) {
        Objects.requireNonNull(expectedSchemaId, "expectedSchemaId");
        return validateHeader(tag, context, expectedSchemaId.toString(), expectedVersion);
    }

    public static CompoundTag preparePayload(
            CompoundTag tag,
            PiDecodeContext context,
            PiStateBinding<?> binding,
            PiSchemaPayloadKind kind
    ) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(kind, "kind");
        String expectedSchemaId = binding.schemaId().toString();
        if (!validateSchemaId(tag, context, expectedSchemaId)) {
            return null;
        }
        Integer rawVersion = readSchemaVersion(tag, context);
        if (rawVersion == null) {
            return null;
        }
        int targetVersion = binding.version();
        if (rawVersion == targetVersion) {
            return tag;
        }
        if (rawVersion > targetVersion) {
            context.issue(
                    PiDecodeIssueCode.SCHEMA_VERSION_MISMATCH,
                    SCHEMA_VERSION_KEY,
                    "expected schema version " + targetVersion + " or older but got " + rawVersion,
                    true
            );
            return null;
        }
        CompoundTag current = tag.copy();
        Map<Integer, PiSchemaMigration> migrations = indexMigrations(binding.migrations(), context);
        if (context.result().hasFatal()) {
            return null;
        }
        int currentVersion = rawVersion;
        while (currentVersion < targetVersion) {
            PiSchemaMigration migration = migrations.get(currentVersion);
            if (migration == null) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "missing schema migration path from version " + currentVersion + " to " + targetVersion,
                        true
                );
                return null;
            }
            CompoundTag migrated;
            try {
                migrated = migration.apply(current.copy(), kind, context);
            } catch (RuntimeException exception) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "schema migration " + currentVersion + " -> " + migration.toVersion() + " failed: " + exception.getMessage(),
                        true
                );
                return null;
            }
            if (migrated == null) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "schema migration " + currentVersion + " -> " + migration.toVersion() + " returned null",
                        true
                );
                return null;
            }
            if (!validateSchemaId(migrated, context, expectedSchemaId)) {
                return null;
            }
            Integer migratedVersion = readSchemaVersion(migrated, context);
            if (migratedVersion == null) {
                return null;
            }
            if (migratedVersion != migration.toVersion()) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "schema migration " + currentVersion + " -> " + migration.toVersion() + " produced version " + migratedVersion,
                        true
                );
                return null;
            }
            if (migratedVersion > targetVersion) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "schema migration " + currentVersion + " -> " + migratedVersion + " overshot target version " + targetVersion,
                        true
                );
                return null;
            }
            current = migrated;
            currentVersion = migratedVersion;
        }
        return currentVersion == targetVersion ? current : null;
    }

    public static CompoundTag upgradePayload(
            String payloadPath,
            int fromVersion,
            int toVersion,
            CompoundTag payload,
            List<PiSchemaMigration> migrations,
            PiDecodeContext context
    ) {
        Objects.requireNonNull(payloadPath, "payloadPath");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(migrations, "migrations");
        Objects.requireNonNull(context, "context");
        if (fromVersion == toVersion) {
            return payload;
        }
        if (fromVersion > toVersion) {
            context.issue(
                    PiDecodeIssueCode.SCHEMA_VERSION_MISMATCH,
                    SCHEMA_VERSION_KEY,
                    payloadPath + " expected version " + toVersion + " or newer but got " + fromVersion,
                    true
            );
            return null;
        }
        CompoundTag current = payload.copy();
        current.putInt(SCHEMA_VERSION_KEY, fromVersion);
        Map<Integer, PiSchemaMigration> indexed = indexMigrations(migrations, context);
        if (context.result().hasFatal()) {
            return null;
        }
        int currentVersion = fromVersion;
        while (currentVersion < toVersion) {
            PiSchemaMigration migration = indexed.get(currentVersion);
            if (migration == null) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        payloadPath + " missing migration path from version " + currentVersion + " to " + toVersion,
                        true
                );
                return null;
            }
            CompoundTag migrated;
            try {
                migrated = migration.apply(current.copy(), PiSchemaPayloadKind.FULL, context);
            } catch (RuntimeException exception) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        payloadPath + " migration " + currentVersion + " -> " + migration.toVersion()
                                + " failed: " + exception.getMessage(),
                        true
                );
                return null;
            }
            if (migrated == null) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        payloadPath + " migration " + currentVersion + " -> " + migration.toVersion() + " returned null",
                        true
                );
                return null;
            }
            Integer migratedVersion = readSchemaVersion(migrated, context);
            if (migratedVersion == null) {
                return null;
            }
            if (migratedVersion != migration.toVersion()) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        payloadPath + " migration " + currentVersion + " -> " + migration.toVersion()
                                + " produced version " + migratedVersion,
                        true
                );
                return null;
            }
            if (migratedVersion > toVersion) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        payloadPath + " migration " + currentVersion + " -> " + migratedVersion
                                + " overshot target version " + toVersion,
                        true
                );
                return null;
            }
            current = migrated;
            currentVersion = migratedVersion;
        }
        return currentVersion == toVersion ? current : null;
    }

    public static Pair<String, Tag> putStringList(String key, List<String> values) {
        ListTag listTag = new ListTag();
        for (String value : values) {
            listTag.add(StringTag.valueOf(value));
        }
        return Pair.of(key, (Tag) listTag);
    }

    public static Pair<String, Tag> putLong(String key, long value) {
        CompoundTag box = new CompoundTag();
        box.putLong(key, value);
        return Pair.of(key, box.get(key));
    }

    public static Pair<String, Tag> putInt(String key, int value) {
        CompoundTag box = new CompoundTag();
        box.putInt(key, value);
        return Pair.of(key, box.get(key));
    }

    public static Pair<String, Tag> putBoolean(String key, boolean value) {
        CompoundTag box = new CompoundTag();
        box.putBoolean(key, value);
        return Pair.of(key, box.get(key));
    }

    public static Pair<String, Tag> putString(String key, String value) {
        CompoundTag box = new CompoundTag();
        box.putString(key, Objects.requireNonNull(value, "Pi schema field " + key + " cannot be null"));
        return Pair.of(key, box.get(key));
    }

    public static Pair<String, Tag> putUUID(String key, UUID value) {
        CompoundTag box = new CompoundTag();
        box.putUUID(key, Objects.requireNonNull(value, "Pi schema field " + key + " cannot be null"));
        return Pair.of(key, box.get(key));
    }

    public static Pair<String, Tag> putResourceLocation(String key, ResourceLocation value) {
        return putString(key, Objects.requireNonNull(value, "Pi schema field " + key + " cannot be null").toString());
    }

    public static List<String> getStringList(CompoundTag tag, String key, PiDecodeContext context) {
        List<String> values = new ArrayList<>();
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing list", false);
            return values;
        }
        if (!(raw instanceof ListTag listTag)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected list tag", false);
            return values;
        }
        if (!listTag.isEmpty() && listTag.getElementType() != Tag.TAG_STRING) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected string list", false);
            return values;
        }
        for (int i = 0; i < listTag.size(); i++) {
            values.add(listTag.getString(i));
        }
        return values;
    }

    public static long getLong(CompoundTag tag, String key, PiDecodeContext context, long defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing long", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_LONG)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected long tag", false);
            return defaultValue;
        }
        return tag.getLong(key);
    }

    public static int getInt(CompoundTag tag, String key, PiDecodeContext context, int defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing int", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_INT)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected int tag", false);
            return defaultValue;
        }
        return tag.getInt(key);
    }

    public static boolean getBoolean(CompoundTag tag, String key, PiDecodeContext context, boolean defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing boolean", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_BYTE)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected boolean tag", false);
            return defaultValue;
        }
        return tag.getBoolean(key);
    }

    public static String getString(CompoundTag tag, String key, PiDecodeContext context, String defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing string", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected string tag", false);
            return defaultValue;
        }
        return tag.getString(key);
    }

    public static UUID getUUID(CompoundTag tag, String key, PiDecodeContext context, UUID defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing uuid", false);
            return defaultValue;
        }
        if (!tag.hasUUID(key)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected uuid tag", false);
            return defaultValue;
        }
        return tag.getUUID(key);
    }

    public static ResourceLocation getResourceLocation(CompoundTag tag, String key, PiDecodeContext context, ResourceLocation defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing resource location", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected string tag", false);
            return defaultValue;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(key));
        if (parsed == null) {
            context.issue(PiDecodeIssueCode.INVALID_VALUE, key, "invalid resource location", false);
            return defaultValue;
        }
        return parsed;
    }

    private static boolean validateSchemaId(CompoundTag tag, PiDecodeContext context, String expectedSchemaId) {
        Tag schemaId = tag.get(SCHEMA_ID_KEY);
        if (schemaId == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, SCHEMA_ID_KEY, "missing schema id", true);
            return false;
        }
        if (!tag.contains(SCHEMA_ID_KEY, Tag.TAG_STRING)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, SCHEMA_ID_KEY, "expected string tag", true);
            return false;
        }
        if (!expectedSchemaId.equals(tag.getString(SCHEMA_ID_KEY))) {
            context.issue(
                    PiDecodeIssueCode.SCHEMA_ID_MISMATCH,
                    SCHEMA_ID_KEY,
                    "expected schema id " + expectedSchemaId + " but got " + tag.getString(SCHEMA_ID_KEY),
                    true
            );
            return false;
        }
        return true;
    }

    private static Integer readSchemaVersion(CompoundTag tag, PiDecodeContext context) {
        Tag version = tag.get(SCHEMA_VERSION_KEY);
        if (version == null) {
            context.issue(PiDecodeIssueCode.SCHEMA_VERSION_MISSING, SCHEMA_VERSION_KEY, "missing schema version", true);
            return null;
        }
        if (!tag.contains(SCHEMA_VERSION_KEY, Tag.TAG_INT)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, SCHEMA_VERSION_KEY, "expected int tag", true);
            return null;
        }
        return tag.getInt(SCHEMA_VERSION_KEY);
    }

    private static Map<Integer, PiSchemaMigration> indexMigrations(List<PiSchemaMigration> migrations, PiDecodeContext context) {
        Map<Integer, PiSchemaMigration> indexed = new LinkedHashMap<>();
        for (PiSchemaMigration migration : migrations) {
            PiSchemaMigration previous = indexed.putIfAbsent(migration.fromVersion(), migration);
            if (previous != null) {
                context.issue(
                        PiDecodeIssueCode.MIGRATION_FAILURE,
                        SCHEMA_VERSION_KEY,
                        "multiple schema migrations registered from version " + migration.fromVersion(),
                        true
                );
                return Map.of();
            }
        }
        return indexed;
    }
}
