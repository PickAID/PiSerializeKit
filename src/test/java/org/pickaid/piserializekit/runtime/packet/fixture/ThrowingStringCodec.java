package org.pickaid.piserializekit.runtime.packet.fixture;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.service.PiSerializer;

public final class ThrowingStringCodec implements PiFieldCodecProvider<String> {
    @Override
    public PiSerializer<String> serializer() {
        return new PiSerializer<>() {
            @Override
            public Codec<String> valueCodec() {
                return Codec.STRING;
            }

            @Override
            public PiNbtCodec<String> nbtCodec() {
                return new PiNbtCodec<>() {
                    @Override
                    public CompoundTag encode(String value) {
                        CompoundTag tag = new CompoundTag();
                        tag.putString(ROOT_VALUE_KEY, value);
                        return tag;
                    }

                    @Override
                    public String decode(CompoundTag tag) {
                        return tag.getString(ROOT_VALUE_KEY);
                    }
                };
            }

            @Override
            public PiPacketCodec<String> packetCodec() {
                return new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, String value) {
                        buffer.writeUtf(value);
                    }

                    @Override
                    public String read(FriendlyByteBuf buffer, PiDecodeContext context) {
                        throw new IllegalStateException("   ");
                    }
                };
            }
        };
    }
}
