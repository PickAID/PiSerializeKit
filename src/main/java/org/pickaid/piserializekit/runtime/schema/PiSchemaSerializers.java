package org.pickaid.piserializekit.runtime.schema;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.packet.PiPacketDecodeException;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeException;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;

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
            public T read(FriendlyByteBuf buffer, PiDecodeContext context) {
                CompoundTag tag = PiPacketSupport.safeRead(context, "", buffer::readNbt, null);
                if (tag == null) {
                    return binding(stateType).newState();
                }
                return decodeState(tag, binding(stateType), context);
            }

            @Override
            public T read(FriendlyByteBuf buffer) {
                PiDecodeContext context = PiDecodeContext.strict();
                T value = read(buffer, context);
                if (context.result().hasIssues()) {
                    throw new PiPacketDecodeException(binding(stateType).schemaId(), context.result());
                }
                return value;
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
        } catch (PiDecodeException exception) {
            return DataResult.error(exception::getMessage);
        } catch (IllegalStateException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static <T> T decodeState(CompoundTag tag, PiStateBinding<T> binding) {
        PiDecodeContext context = PiDecodeContext.strict();
        T state = decodeState(tag, binding, context);
        if (context.result().hasIssues()) {
            throw new PiDecodeException(binding.schemaId(), context.result());
        }
        return state;
    }

    private static <T> T decodeState(CompoundTag tag, PiStateBinding<T> binding, PiDecodeContext context) {
        T state = binding.newState();
        CompoundTag payload = PiSchemaSupport.preparePayload(tag, context, binding, PiSchemaPayloadKind.FULL);
        if (payload != null) {
            binding.loadFull(state, payload, context);
        }
        return state;
    }
}
