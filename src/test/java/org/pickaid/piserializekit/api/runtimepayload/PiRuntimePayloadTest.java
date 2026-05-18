package org.pickaid.piserializekit.api.runtimepayload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketRegistry;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PiRuntimePayloadTest {
    @Test
    void cuePayloadCarriesTypeSequenceAndReferenceWithoutFriendlyByteBuf() {
        PiRuntimePayload payload = PiCuePayload.of(
                new ResourceLocation("test", "beam"),
                42L,
                PiEntityRef.byId(7)
        );

        assertEquals(new ResourceLocation("test", "beam"), payload.type());
        assertEquals(42L, payload.sequence());
    }

    @Test
    void referenceCarriersUseStablePrimitiveIds() {
        UUID entityUuid = UUID.fromString("00000000-0000-0000-0000-000000000123");
        PiEntityRef entityRef = PiEntityRef.byUuid(entityUuid);
        PiDimensionRef dimensionRef = PiDimensionRef.of(new ResourceLocation("minecraft", "overworld"));
        PiBlockRef blockRef = PiBlockRef.at(dimensionRef, new BlockPos(1, 2, 3));
        PiRegistryIdRef registryRef = PiRegistryIdRef.of(
                new ResourceLocation("minecraft", "item"),
                new ResourceLocation("minecraft", "diamond")
        );

        assertEquals(entityUuid, entityRef.uuid().orElseThrow());
        assertEquals(new ResourceLocation("minecraft", "overworld"), blockRef.dimension().id());
        assertEquals(new BlockPos(1, 2, 3), blockRef.pos());
        assertEquals(new ResourceLocation("minecraft", "diamond"), registryRef.id());
    }

    @Test
    void runtimePayloadBindingRegistersAndRoundTripsThroughPacketBufferSurface() {
        PiPacketRegistry registry = new TestPacketRegistry();
        PiPacketBinding<PiCuePayload> binding = PiRuntimePayloadBinding.cue(
                new ResourceLocation("test", "cue"),
                new ResourceLocation("test", "cue_channel")
        );
        registry.register(PiCuePayload.class, binding);
        PiCuePayload payload = PiCuePayload.of(new ResourceLocation("test", "beam"), 42L, PiEntityRef.byId(7));

        PiPacketBuffer buffer = PiPacketBuffers.heap();
        registry.require(PiCuePayload.class).codec().write(buffer, payload);
        PiCuePayload decoded = registry.require(PiCuePayload.class).codec().read(buffer);

        assertEquals(payload.type(), decoded.type());
        assertEquals(payload.sequence(), decoded.sequence());
        assertEquals(payload.source(), decoded.source());
    }

    private static final class TestPacketRegistry implements PiPacketRegistry {
        private final Map<Class<?>, PiPacketBinding<?>> byType = new HashMap<>();
        private final Map<ResourceLocation, PiPacketBinding<?>> byId = new HashMap<>();

        @Override
        public <T> void register(Class<T> type, PiPacketBinding<T> binding) {
            byType.put(type, binding);
            byId.put(binding.packetId(), binding);
        }

        @Override
        public <T> Optional<PiPacketBinding<T>> find(Class<T> type) {
            return Optional.ofNullable(cast(byType.get(type)));
        }

        @Override
        public <T> PiPacketBinding<T> require(Class<T> type) {
            return find(type).orElseThrow();
        }

        @Override
        public Optional<PiPacketBinding<?>> find(ResourceLocation packetId) {
            return Optional.ofNullable(byId.get(packetId));
        }

        @Override
        public PiPacketBinding<?> require(ResourceLocation packetId) {
            return find(packetId).orElseThrow();
        }

        @SuppressWarnings("unchecked")
        private static <T> PiPacketBinding<T> cast(PiPacketBinding<?> binding) {
            return (PiPacketBinding<T>) binding;
        }
    }
}
