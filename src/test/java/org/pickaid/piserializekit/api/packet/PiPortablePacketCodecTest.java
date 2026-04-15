package org.pickaid.piserializekit.api.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketCodecs;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffers;
import org.pickaid.piserializekit.api.packet.buffer.PiPortablePacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;

class PiPortablePacketCodecTest {
    @Test
    void portablePacketCodecBridgesThroughFriendlyByteBuf() {
        PiPacketCodec<TestPacket> codec = PiPacketCodecs.fromPortable(new PiPortablePacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, TestPacket value) {
                buffer.writeUtf(value.skill());
                buffer.writeVarInt(value.level());
            }

            @Override
            public TestPacket read(PiPacketBuffer buffer, org.pickaid.piserializekit.api.schema.PiDecodeContext context) {
                return new TestPacket(buffer.readUtf(64), buffer.readVarInt());
            }
        });

        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.write(byteBuf, new TestPacket("fireball", 2));
            TestPacket decoded = codec.read(byteBuf);

            assertEquals(new TestPacket("fireball", 2), decoded);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    void legacyPacketCodecCanBeViewedThroughPortableBuffer() {
        PiPacketCodec<TestPacket> legacy = new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, TestPacket value) {
                buffer.writeUtf(value.skill());
                buffer.writeVarInt(value.level());
            }

            @Override
            public TestPacket read(PiPacketBuffer buffer, org.pickaid.piserializekit.api.schema.PiDecodeContext context) {
                return new TestPacket(buffer.readUtf(), buffer.readVarInt());
            }
        };

        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PiPacketBuffer buffer = PiPacketBuffers.wrap(byteBuf);
            PiPortablePacketCodec<TestPacket> portable = PiPacketCodecs.portable(legacy);

            portable.write(buffer, new TestPacket("blink", 5));
            TestPacket decoded = portable.read(buffer);

            assertEquals(new TestPacket("blink", 5), decoded);
        } finally {
            byteBuf.release();
        }
    }

    @Test
    void legacyPacketCodecAdapterCapturesThrownRuntimeExceptionAsStructuredIssue() {
        PiPacketCodec<TestPacket> legacy = new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, TestPacket value) {
            }

            @Override
            public TestPacket read(PiPacketBuffer buffer, PiDecodeContext context) {
                throw new IllegalStateException("boom");
            }
        };

        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PiDecodeContext context = PiDecodeContext.strict();
            TestPacket decoded = PiPacketCodecs.portable(legacy).read(PiPacketBuffers.wrap(byteBuf), context);

            assertNull(decoded);
            assertTrue(context.result().hasFatal());
            assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
            assertEquals("$", context.result().issues().get(0).path());
            assertEquals("boom", context.result().issues().get(0).message());
        } finally {
            byteBuf.release();
        }
    }

    @Test
    void friendlyPacketBufferHasNoCapabilitiesByDefault() {
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PiPacketBuffer buffer = PiPacketBuffers.wrap(byteBuf);
            assertTrue(buffer.capability(TestCapability.class).isEmpty());
        } finally {
            byteBuf.release();
        }
    }

    private record TestPacket(String skill, int level) {
    }

    private static final class TestCapability {
    }
}
