package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.packet.PiPacketDirection;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PiRuntimePayloadBinding {
    private PiRuntimePayloadBinding() {
    }

    public static PiPacketBinding<PiCuePayload> cue(ResourceLocation packetId, ResourceLocation channel) {
        Objects.requireNonNull(channel, "channel");
        return new CueBinding(Objects.requireNonNull(packetId, "packetId"));
    }

    private record CueBinding(ResourceLocation packetId) implements PiPacketBinding<PiCuePayload> {
        private static final List<PiFieldKey> FIELDS = List.of(
                new PiFieldKey(0, "type"),
                new PiFieldKey(1, "sequence"),
                new PiFieldKey(2, "entity_id"),
                new PiFieldKey(3, "entity_uuid"),
                new PiFieldKey(4, "data")
        );

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.BIDIRECTIONAL;
        }

        @Override
        public Class<PiCuePayload> packetType() {
            return PiCuePayload.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return FIELDS;
        }

        @Override
        public PiPacketCodec<PiCuePayload> codec() {
            return PiCuePayloadCodec.INSTANCE;
        }
    }

    private enum PiCuePayloadCodec implements PiPacketCodec<PiCuePayload> {
        INSTANCE;

        @Override
        public void write(PiPacketBuffer buffer, PiCuePayload value) {
            buffer.writeResourceLocation(value.type());
            buffer.writeVarLong(value.sequence());
            writeEntityRef(buffer, value.source());
            buffer.writeNbt(value.data());
        }

        @Override
        public PiCuePayload read(PiPacketBuffer buffer, PiDecodeContext context) {
            ResourceLocation type = buffer.readResourceLocation();
            long sequence = buffer.readVarLong();
            PiEntityRef source = readEntityRef(buffer);
            return new PiCuePayload(type, sequence, source, buffer.readNbt());
        }

        private static void writeEntityRef(PiPacketBuffer buffer, PiEntityRef ref) {
            buffer.writeBoolean(ref.entityId().isPresent());
            ref.entityId().ifPresent(buffer::writeVarInt);
            buffer.writeBoolean(ref.uuid().isPresent());
            ref.uuid().ifPresent(buffer::writeUuid);
        }

        private static PiEntityRef readEntityRef(PiPacketBuffer buffer) {
            Optional<Integer> entityId = buffer.readBoolean() ? Optional.of(buffer.readVarInt()) : Optional.empty();
            Optional<java.util.UUID> uuid = buffer.readBoolean() ? Optional.of(buffer.readUuid()) : Optional.empty();
            return new PiEntityRef(entityId, uuid);
        }
    }
}
