package org.pickaid.piserializekit.runtime.packet.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.PiPacket;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.api.packet.PiServerPacket;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

@PiPacket(version = 2)
public final class LegacySkillPacket extends PiServerPacket {
    @PiField(id = "skill_id", sync = PiSyncScope.OWNER, persist = false)
    public ResourceLocation skillId;

    @PiField(id = "target", sync = PiSyncScope.OWNER, persist = false)
    public BlockPos target;

    public LegacySkillPacket(ResourceLocation skillId, BlockPos target) {
        this.skillId = skillId;
        this.target = target;
    }

    @PiPacketUpgrade(from = 1, to = 2)
    public static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {
        payload.put(
                "target",
                PiSerializeServices.require().lookup(PiSerializers.BLOCK_POS).orElseThrow().nbtCodec().encode(BlockPos.ZERO)
        );
        payload.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, 2);
        return payload;
    }
}
