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
import org.pickaid.piserializekit.runtime.schema.PiSchemaFieldCodecs;
import org.pickaid.piserializekit.runtime.schema.PiSchemaSupport;

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
            String message = exception.getMessage();
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    path,
                    message == null ? "packet decode failed" : message,
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
        if (legacy) {
            if (!buffer.isReadable()) {
                return null;
            }
            try {
                return serializer.packetCodec().read(buffer);
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return serializer.packetCodec().read(buffer, context.child(path));
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
            payload.put(key, PiSchemaFieldCodecs.encode(key, serializer, value).getSecond());
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            context.issue(
                    PiDecodeIssueCode.SERIALIZER_FAILURE,
                    key,
                    message == null ? "failed to encode packet payload field" : message,
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
