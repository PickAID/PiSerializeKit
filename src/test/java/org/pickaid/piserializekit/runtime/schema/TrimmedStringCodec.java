package org.pickaid.piserializekit.runtime.schema;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
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
                new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, String value) {
                        buffer.writeUtf(value);
                    }

                    @Override
                    public String read(FriendlyByteBuf buffer, PiDecodeContext context) {
                        return PiPacketSupport.safeRead(context, "", () -> buffer.readUtf().trim(), "");
                    }
                }
        );
    }
}
