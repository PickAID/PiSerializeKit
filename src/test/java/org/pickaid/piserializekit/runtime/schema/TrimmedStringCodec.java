package org.pickaid.piserializekit.runtime.schema;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;

public final class TrimmedStringCodec implements PiFieldCodecProvider<String> {
    @Override
    public PiSerializer<String> serializer() {
        return PiSerializers.of(
                Codec.STRING.xmap(String::trim, value -> value),
                new PiNbtCodec<>() {
                    @Override
                    public CompoundTag encode(String value) {
                        CompoundTag wrapped = new CompoundTag();
                        wrapped.put(ROOT_VALUE_KEY, encodeTag(value));
                        return wrapped;
                    }

                    @Override
                    public Tag encodeTag(String value) {
                        return StringTag.valueOf(value);
                    }

                    @Override
                    public String decode(CompoundTag tag) {
                        return decodeTag(tag.contains(ROOT_VALUE_KEY) && tag.getAllKeys().size() == 1 ? tag.get(ROOT_VALUE_KEY) : tag);
                    }

                    @Override
                    public String decodeTag(Tag tag) {
                        if (tag instanceof StringTag stringTag) {
                            return stringTag.getAsString().trim();
                        }
                        throw new IllegalStateException("Expected string tag");
                    }
                },
                new PiPacketCodec<>() {
                    @Override
                    public void write(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, String value) {
                        buffer.writeUtf(value);
                    }

                    @Override
                    public String read(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, PiDecodeContext context) {
                        return PiPacketSupport.safeRead(context, "", () -> buffer.readUtf().trim(), "");
                    }
                }
        );
    }
}
