package org.pickaid.piserializekit.runtime.schema;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.service.PiSerializer;

/**
 * Serializer adapters backed by generated Pi schema bindings.
 */
public final class PiSchemaSerializers {
    private PiSchemaSerializers() {
    }

    public static <T> PiSerializer<T> forState(Class<T> stateType) {
        Objects.requireNonNull(stateType, "stateType");
        Codec<T> codec = Codec.PASSTHROUGH.comapFlatMap(
                dynamic -> decodeToResult(dynamic.convert(NbtOps.INSTANCE).getValue(), binding(stateType)),
                value -> new Dynamic<>(NbtOps.INSTANCE, binding(stateType).saveFull(value).copy())
        );
        PiNbtCodec<T> nbtCodec = new PiNbtCodec<>() {
            @Override
            public CompoundTag encode(T value) {
                return binding(stateType).saveFull(value).copy();
            }

            @Override
            public T decode(CompoundTag tag) {
                return decodeState(tag, binding(stateType));
            }
        };
        PiPacketCodec<T> packetCodec = new PiPacketCodec<>() {
            @Override
            public void write(FriendlyByteBuf buffer, T value) {
                buffer.writeNbt(binding(stateType).saveFull(value));
            }

            @Override
            public T read(FriendlyByteBuf buffer) {
                CompoundTag tag = buffer.readNbt();
                if (tag == null) {
                    throw new IllegalStateException("Missing Pi schema payload for " + binding(stateType).schemaId());
                }
                return decodeState(tag, binding(stateType));
            }
        };
        return new PiSerializer<>() {
            @Override
            public Codec<T> valueCodec() {
                return codec;
            }

            @Override
            public PiNbtCodec<T> nbtCodec() {
                return nbtCodec;
            }

            @Override
            public PiPacketCodec<T> packetCodec() {
                return packetCodec;
            }
        };
    }

    private static <T> PiStateBinding<T> binding(Class<T> stateType) {
        return PiSchemas.require(stateType);
    }

    private static <T> DataResult<T> decodeToResult(Tag raw, PiStateBinding<T> binding) {
        if (!(raw instanceof CompoundTag compoundTag)) {
            return DataResult.error(() -> "Expected compound payload for Pi schema " + binding.schemaId());
        }
        try {
            return DataResult.success(decodeState(compoundTag, binding));
        } catch (IllegalStateException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static <T> T decodeState(CompoundTag tag, PiStateBinding<T> binding) {
        T state = binding.newState();
        PiDecodeContext context = PiDecodeContext.strict();
        binding.loadFull(state, tag.copy(), context);
        if (!context.result().issues().isEmpty()) {
            throw new IllegalStateException(
                    "Failed to decode Pi schema " + binding.schemaId() + ": " + context.result().issues().stream()
                            .map(issue -> issue.path() + " -> " + issue.message())
                            .collect(Collectors.joining("; "))
            );
        }
        return state;
    }
}
