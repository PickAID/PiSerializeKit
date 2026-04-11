package org.pickaid.piserializekit.runtime.schema;

import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.service.PiSerializer;

/**
 * Generic serializer-backed field codecs used by generated schemas.
 */
public final class PiSchemaFieldCodecs {
    private static final String ROOT_VALUE_KEY = "__pi_value";

    private PiSchemaFieldCodecs() {
    }

    public static <T> Pair<String, Tag> writeField(PiSchemaField<T> field, T value) {
        Objects.requireNonNull(field, "field");
        return encode(field.key(), field.serializer(), value);
    }

    public static <T> Pair<String, Tag> encode(String key, PiSerializer<T> serializer, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        return Pair.of(key, serializer.nbtCodec().encode(value).copy());
    }

    public static <T> T readField(CompoundTag root, PiSchemaField<T> field, PiDecodeContext context, T fallback) {
        Objects.requireNonNull(field, "field");
        return decode(root, field.key(), field.serializer(), context, fallback);
    }

    public static <T> T decode(CompoundTag root, String key, PiSerializer<T> serializer, PiDecodeContext context, T fallback) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        Tag raw = root.get(key);
        if (raw == null) {
            context.issue(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, key, "missing field payload", false);
            return fallback;
        }
        if (raw instanceof CompoundTag compound) {
            return decodePayload(key, compound, serializer, context, fallback, "field payload");
        }
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(ROOT_VALUE_KEY, raw.copy());
        return decodePayload(key, wrapped, serializer, context, fallback, "legacy field payload");
    }

    private static <T> T decodePayload(
            String key,
            CompoundTag payload,
            PiSerializer<T> serializer,
            PiDecodeContext context,
            T fallback,
            String label
    ) {
        try {
            return serializer.nbtCodec().decode(payload);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    key,
                    "failed to decode " + label + (message == null ? "" : ": " + message),
                    false
            );
            return fallback;
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
