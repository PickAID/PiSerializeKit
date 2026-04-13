package org.pickaid.piserializekit.runtime.schema.codec;

import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

/**
 * Generic serializer-backed field codecs used by generated schemas.
 */
public final class PiSchemaFieldCodecs {
    private PiSchemaFieldCodecs() {
    }

    public static <T> Pair<String, Tag> writeField(PiSchemaField<T> field, T value) {
        Objects.requireNonNull(field, "field");
        return Pair.of(field.key(), encodeField(field, value));
    }

    public static <T> void writeField(CompoundTag root, PiSchemaField<T> field, T value) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(field, "field");
        root.put(field.key(), encodeField(field, value));
    }

    public static <T> Pair<String, Tag> encode(String key, PiSerializer<T> serializer, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        return Pair.of(key, encodeValue(serializer, value));
    }

    public static <T> Tag encodeField(PiSchemaField<T> field, T value) {
        Objects.requireNonNull(field, "field");
        return encodeValue(field.serializer(), value);
    }

    public static <T> Tag encodeValue(PiSerializer<T> serializer, T value) {
        Objects.requireNonNull(serializer, "serializer");
        return serializer.nbtCodec().encodeTag(value);
    }

    public static <T> T readField(CompoundTag root, PiSchemaField<T> field, PiDecodeContext context, T fallback) {
        Objects.requireNonNull(field, "field");
        return decode(root, field.key(), field.serializer(), context, fallback);
    }

    public static <T> T readFieldOrNull(CompoundTag root, PiSchemaField<T> field, PiDecodeContext context) {
        Objects.requireNonNull(field, "field");
        return decode(root, field.key(), field.serializer(), context, null);
    }

    public static <T> T readFieldInto(CompoundTag root, PiSchemaField<T> field, PiDecodeContext context, T current) {
        Objects.requireNonNull(field, "field");
        return decodeInto(root, field.key(), field.serializer(), context, current);
    }

    public static <T> T decode(CompoundTag root, String key, PiSerializer<T> serializer, PiDecodeContext context, T fallback) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        Tag raw = root.get(key);
        if (raw == null) {
            if (context.hasIssue(key)) {
                return fallback;
            }
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing field payload", false);
            return fallback;
        }
        return decodePayload(key, raw, serializer, context, fallback, raw instanceof CompoundTag ? "field payload" : "legacy field payload");
    }

    public static <T> T decodeInto(CompoundTag root, String key, PiSerializer<T> serializer, PiDecodeContext context, T current) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        Tag raw = root.get(key);
        if (raw == null) {
            if (context.hasIssue(key)) {
                return current;
            }
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing field payload", false);
            return current;
        }
        return decodePayloadInto(key, raw, serializer, context, current, raw instanceof CompoundTag ? "field payload" : "legacy field payload");
    }

    private static <T> T decodePayload(
            String key,
            Tag payload,
            PiSerializer<T> serializer,
            PiDecodeContext context,
            T fallback,
            String label
    ) {
        try {
            return serializer.nbtCodec().decodeTag(payload);
        } catch (RuntimeException exception) {
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    key,
                    PiSchemaSupport.describeException(exception, "failed to decode " + label),
                    false
            );
            return fallback;
        }
    }

    private static <T> T decodePayloadInto(
            String key,
            Tag payload,
            PiSerializer<T> serializer,
            PiDecodeContext context,
            T current,
            String label
    ) {
        try {
            return serializer.nbtCodec().decodeIntoTag(payload, current);
        } catch (RuntimeException exception) {
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    key,
                    PiSchemaSupport.describeException(exception, "failed to decode " + label),
                    false
            );
            return current;
        }
    }

    public static <T> T readNestedField(
            CompoundTag root,
            String key,
            PiStateBinding<T> binding,
            PiDecodeContext context,
            T current,
            PiSchemaPayloadKind kind
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(kind, "kind");
        Tag raw = root.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing field payload", false);
            return current;
        }
        if (!(raw instanceof CompoundTag compound)) {
            context.issue(PiDecodeIssueCode.TYPE_MISMATCH, key, "expected compound field payload", false);
            return current;
        }
        T target = current != null ? current : binding.newState();
        switch (kind) {
            case FULL -> binding.loadFull(target, compound, context);
            case PERSISTED -> binding.loadPersisted(target, compound, context);
            case DELTA -> binding.applyDelta(target, compound, context);
        }
        return target;
    }
}
