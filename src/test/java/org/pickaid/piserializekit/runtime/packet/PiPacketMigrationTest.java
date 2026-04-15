package org.pickaid.piserializekit.runtime.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssue;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;
import org.pickaid.piserializekit.runtime.packet.fixture.LegacySkillPacket;

class PiPacketMigrationTest {
    @Test
    void olderPacketVersionUpgradesBeforeConstruction() {
        PiPacketBinding<LegacySkillPacket> binding = PiPackets.require(LegacySkillPacket.class);
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
        List<PiSchemaMigration> migrations = binding.migrations();
        assertEquals(1, migrations.size());
        assertEquals(1, migrations.get(0).fromVersion());
        assertEquals(2, migrations.get(0).toVersion());
        assertFalse(context.result().hasIssues(), () -> "Unexpected issues: " + context.result().issues());
    }

    @Test
    void packetPayloadUpgradeReportsDeclaredStepsWhenMigrationChainIsIncomplete() {
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag payload = new CompoundTag();
        CompoundTag migrated = PiPacketSupport.upgradePacketPayload(
                "test:legacy_packet",
                1,
                4,
                payload,
                List.of(PiSchemaMigration.step(1, 2, (current, kind, decodeContext) -> {
                    CompoundTag upgraded = current.copy();
                    upgraded.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, 2);
                    return upgraded;
                })),
                context
        );

        assertNull(migrated);
        assertTrue(context.result().hasFatal());
        assertEquals(
                "test:legacy_packet missing migration path from version 2 to 4; declared steps: 1->2",
                context.result().issues().get(0).message()
        );
    }

    @Test
    void legacyPacketDecodeRetainsOriginalFieldFailure() {
        PiPacketBinding<LegacySkillPacket> binding = PiPackets.require(LegacySkillPacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("Bad Value");

        PiDecodeContext context = PiDecodeContext.strict();
        binding.codec().read(buffer, context);

        List<PiDecodeIssue> issues = context.result().issues();
        assertTrue(
                issues.stream().anyMatch(issue ->
                        issue.code() == PiDecodeIssueCode.SERIALIZER_FAILURE
                                && issue.path().equals("skill_id")
                )
        );
        assertTrue(
                issues.stream().noneMatch(issue -> issue.path().equals("skill_id.skill_id"))
        );
        assertTrue(
                issues.stream().noneMatch(issue ->
                        issue.code() == PiDecodeIssueCode.MISSING_FIELD_PAYLOAD
                                && issue.path().equals("skill_id")
                )
        );
    }
}
