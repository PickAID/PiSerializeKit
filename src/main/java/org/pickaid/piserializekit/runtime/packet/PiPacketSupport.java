package org.pickaid.piserializekit.runtime.packet;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaFieldCodecs;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

/**
 * Packet decode helpers shared by generated bindings and packet serializers.
 */
public final class PiPacketSupport {
    private PiPacketSupport() {
    }

    public static <T> T safeRead(PiDecodeContext context, String path, Supplier<T> reader, T fallback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reader, "reader");
        try {
            return reader.get();
        } catch (RuntimeException exception) {
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    path,
                    PiSchemaSupport.describeException(exception, "packet decode failed"),
                    true
            );
            return fallback;
        }
    }

    public static <T> T readIncomingField(
            FriendlyByteBuf buffer,
            String path,
            PiSerializer<T> serializer,
            PiDecodeContext context,
            boolean legacy,
            T fallback
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        PiDecodeContext fieldContext = context.child(path);
        if (legacy) {
            if (!buffer.isReadable()) {
                return null;
            }
        }
        return readNestedValue(buffer, serializer, fieldContext, legacy ? null : fallback);
    }

    /**
     * Decodes one nested packet value while converting raw runtime failures into structured issues.
     *
     * <p>This is used by composite serializers so list/set/map/optional/array packet paths stay on
     * the same diagnostics model as generated packet bindings.</p>
     */
    public static <T> T readNestedValue(
            FriendlyByteBuf buffer,
            PiSerializer<T> serializer,
            PiDecodeContext context,
            T fallback
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        try {
            return serializer.packetCodec().read(buffer, context);
        } catch (RuntimeException exception) {
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    "",
                    PiSchemaSupport.describeException(exception),
                    true
            );
            return fallback;
        }
    }

    public static <T> void writePayloadField(
            CompoundTag payload,
            String key,
            PiSerializer<T> serializer,
            T value,
            PiDecodeContext context
    ) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(context, "context");
        if (value == null) {
            return;
        }
        try {
            payload.put(key, PiSchemaFieldCodecs.encodeValue(serializer, value));
        } catch (RuntimeException exception) {
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    key,
                    PiSchemaSupport.describeException(exception, "failed to encode packet payload field"),
                    true
            );
        }
    }

    public static CompoundTag upgradePacketPayload(
            String packetPath,
            int fromVersion,
            int toVersion,
            CompoundTag payload,
            List<PiSchemaMigration> migrations,
            PiDecodeContext context
    ) {
        return PiSchemaSupport.upgradePayload(packetPath, fromVersion, toVersion, payload, migrations, context);
    }
}
