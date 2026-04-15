package org.pickaid.piserializekit.api.packet.buffer;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodecDecodeException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssue;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;

/**
 * Shared adapters between legacy and portable packet codec contracts.
 */
public final class PiPacketCodecs {
    private PiPacketCodecs() {
    }

    public static <T> PiPacketCodec<T> fromPortable(PiPortablePacketCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, T value) {
                codec.write(buffer, value);
            }

            @Override
            public T read(PiPacketBuffer buffer, PiDecodeContext context) {
                return codec.read(buffer, context);
            }
        };
    }

    public static <T> PiPortablePacketCodec<T> portable(PiPacketCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, T value) {
                codec.write(buffer, value);
            }

            @Override
            public T read(PiPacketBuffer buffer, PiDecodeContext context) {
                try {
                    return codec.read(buffer, context);
                } catch (PiPacketCodecDecodeException exception) {
                    mergeStructuredIssues(context, exception);
                    return null;
                } catch (RuntimeException exception) {
                    context.issue(
                            PiDecodeIssueCode.SERIALIZER_FAILURE,
                            "",
                            describeException(exception),
                            true
                    );
                    return null;
                }
            }
        };
    }

    private static void mergeStructuredIssues(PiDecodeContext context, PiPacketCodecDecodeException exception) {
        if (!exception.result().hasIssues()) {
            context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, "", exception.getMessage(), true);
            return;
        }
        for (PiDecodeIssue issue : exception.result().issues()) {
            context.issue(issue.code(), relativize(issue.path()), issue.message(), issue.fatal());
        }
    }

    private static String relativize(String path) {
        if (path == null || path.isBlank() || path.equals("$")) {
            return "";
        }
        if (path.startsWith("$."))
            return path.substring(2);
        if (path.startsWith("$["))
            return path.substring(1);
        return path;
    }

    private static String describeException(RuntimeException exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            return "packet decode failed: " + exception.getClass().getSimpleName();
        }
        return detail;
    }
}
