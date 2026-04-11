package org.pickaid.piserializekit.runtime.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketDecodeException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.runtime.packet.fixture.TestNoticePacket;

class PiPacketsTest {
    @Test
    void packetBindingsLoadByTypeAndPacketId() {
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "test_notice"), binding.packetId());
        assertSame(binding, PiPackets.require(ResourceLocation.fromNamespaceAndPath("test", "test_notice")));
    }

    @Test
    void packetCodecReportsNestedDecodePathThroughContext() {
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("alert");
        buffer.writeVarInt(1);

        PiDecodeContext context = PiDecodeContext.strict();
        TestNoticePacket decoded = binding.codec().read(buffer, context);

        assertEquals("alert", decoded.title);
        assertEquals(List.of(""), decoded.lines);
        assertTrue(context.result().hasFatal());
        assertEquals("lines[0]", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
    }

    @Test
    void strictPacketReadThrowsStructuredDecodeException() {
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("alert");
        buffer.writeVarInt(1);

        PiPacketDecodeException exception = assertThrows(PiPacketDecodeException.class, () -> binding.codec().read(buffer));

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "test_notice"), exception.packetId());
        assertTrue(exception.result().hasFatal());
    }
}
