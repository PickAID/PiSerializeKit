package org.pickaid.piserializekit.runtime.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.packet.fixture.LegacySkillPacket;

class PiPacketMigrationTest {
    @Test
    void olderPacketVersionUpgradesBeforeConstruction() {
        PiPacketBinding<LegacySkillPacket, ?> binding = PiPackets.require(LegacySkillPacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        PiSerializeServices.require().lookup(PiSerializers.RESOURCE_LOCATION).orElseThrow().packetCodec().write(
                buffer,
                ResourceLocation.fromNamespaceAndPath("example", "blink")
        );

        PiDecodeContext context = PiDecodeContext.strict();
        LegacySkillPacket decoded = binding.codec().read(buffer, context);

        assertEquals(ResourceLocation.fromNamespaceAndPath("example", "blink"), decoded.skillId);
        assertEquals(BlockPos.ZERO, decoded.target);
        assertEquals(2, binding.version());
        assertFalse(context.result().hasIssues());
    }
}
