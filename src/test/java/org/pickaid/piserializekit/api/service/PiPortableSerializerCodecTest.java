package org.pickaid.piserializekit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffers;
import org.pickaid.piserializekit.api.packet.buffer.PiPortablePacketCodec;

class PiPortableSerializerCodecTest {
    @Test
    void serializerCanBeBuiltFromPortablePacketCodec() {
        PiSerializer<TestPayload> serializer = PiSerializers.of(
                Codec.STRING.xmap(value -> new TestPayload(value, value.length()), TestPayload::skill),
                new PiPortablePacketCodec<>() {
                    @Override
                    public void write(PiPacketBuffer buffer, TestPayload value) {
                        buffer.writeUtf(value.skill());
                        buffer.writeVarInt(value.level());
                    }

                    @Override
                    public TestPayload read(PiPacketBuffer buffer, org.pickaid.piserializekit.api.schema.PiDecodeContext context) {
                        return new TestPayload(buffer.readUtf(64), buffer.readVarInt());
                    }
                }
        );

        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.packetCodec().write(byteBuf, new TestPayload("meteor", 6));
            TestPayload decoded = serializer.packetCodec().read(byteBuf);

            assertEquals(new TestPayload("meteor", 6), decoded);
            assertEquals(new TestPayload("meteor", 6), serializer.portablePacketCodec().read(PiPacketBuffers.wrap(byteBuf = reset(byteBuf))));
        } finally {
            byteBuf.release();
        }
    }

    private static FriendlyByteBuf reset(FriendlyByteBuf buffer) {
        buffer.readerIndex(0);
        return buffer;
    }

    private record TestPayload(String skill, int level) {
    }
}
