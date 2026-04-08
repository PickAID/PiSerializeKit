package org.pickaid.piserializekit.runtime.schema;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;

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
        Tag schemaId = tag.get(SCHEMA_ID_KEY);
        if (schemaId == null) {
            context.issue(SCHEMA_ID_KEY, "missing schema id", true);
            valid = false;
        } else if (!tag.contains(SCHEMA_ID_KEY, Tag.TAG_STRING)) {
            context.issue(SCHEMA_ID_KEY, "expected string tag", true);
            valid = false;
        } else if (!expectedSchemaId.equals(tag.getString(SCHEMA_ID_KEY))) {
            context.issue(SCHEMA_ID_KEY, "expected schema id " + expectedSchemaId + " but got " + tag.getString(SCHEMA_ID_KEY), true);
            valid = false;
        }

        Tag version = tag.get(SCHEMA_VERSION_KEY);
        if (version == null) {
            context.issue(SCHEMA_VERSION_KEY, "missing schema version", true);
            valid = false;
        } else if (!tag.contains(SCHEMA_VERSION_KEY, Tag.TAG_INT)) {
            context.issue(SCHEMA_VERSION_KEY, "expected int tag", true);
            valid = false;
        } else if (expectedVersion != tag.getInt(SCHEMA_VERSION_KEY)) {
            context.issue(SCHEMA_VERSION_KEY, "expected schema version " + expectedVersion + " but got " + tag.getInt(SCHEMA_VERSION_KEY), true);
            valid = false;
        }
        return valid;
    }

    public static boolean validateHeader(CompoundTag tag, PiDecodeContext context, ResourceLocation expectedSchemaId, int expectedVersion) {
        Objects.requireNonNull(expectedSchemaId, "expectedSchemaId");
        return validateHeader(tag, context, expectedSchemaId.toString(), expectedVersion);
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
            context.issue(key, "missing list", false);
            return values;
        }
        if (!(raw instanceof ListTag listTag)) {
            context.issue(key, "expected list tag", false);
            return values;
        }
        if (!listTag.isEmpty() && listTag.getElementType() != Tag.TAG_STRING) {
            context.issue(key, "expected string list", false);
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
            context.issue(key, "missing long", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_LONG)) {
            context.issue(key, "expected long tag", false);
            return defaultValue;
        }
        return tag.getLong(key);
    }

    public static int getInt(CompoundTag tag, String key, PiDecodeContext context, int defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(key, "missing int", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_INT)) {
            context.issue(key, "expected int tag", false);
            return defaultValue;
        }
        return tag.getInt(key);
    }

    public static boolean getBoolean(CompoundTag tag, String key, PiDecodeContext context, boolean defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(key, "missing boolean", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_BYTE)) {
            context.issue(key, "expected boolean tag", false);
            return defaultValue;
        }
        return tag.getBoolean(key);
    }

    public static String getString(CompoundTag tag, String key, PiDecodeContext context, String defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(key, "missing string", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            context.issue(key, "expected string tag", false);
            return defaultValue;
        }
        return tag.getString(key);
    }

    public static UUID getUUID(CompoundTag tag, String key, PiDecodeContext context, UUID defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(key, "missing uuid", false);
            return defaultValue;
        }
        if (!tag.hasUUID(key)) {
            context.issue(key, "expected uuid tag", false);
            return defaultValue;
        }
        return tag.getUUID(key);
    }

    public static ResourceLocation getResourceLocation(CompoundTag tag, String key, PiDecodeContext context, ResourceLocation defaultValue) {
        Tag raw = tag.get(key);
        if (raw == null) {
            context.issue(key, "missing resource location", false);
            return defaultValue;
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            context.issue(key, "expected string tag", false);
            return defaultValue;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(key));
        if (parsed == null) {
            context.issue(key, "invalid resource location", false);
            return defaultValue;
        }
        return parsed;
    }
}
