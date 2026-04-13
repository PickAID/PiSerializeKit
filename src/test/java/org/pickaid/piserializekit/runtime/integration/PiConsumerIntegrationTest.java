package org.pickaid.piserializekit.runtime.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.packet.PiPackets;
import org.pickaid.piserializekit.runtime.packet.fixture.TestNoticePacket;
import org.pickaid.piserializekit.runtime.schema.GeneratedComplexState;
import org.pickaid.piserializekit.runtime.schema.registry.PiSchemas;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;

class PiConsumerIntegrationTest {
    private static final ResourceLocation GENERATED_COMPLEX_STATE_ID =
            ResourceLocation.fromNamespaceAndPath("test", "generated_complex_state");
    private static final ResourceLocation TEST_NOTICE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("test", "test_notice");

    @Test
    void consumerResolvesGeneratedBindingsByAuthoredTypeAndStableId() {
        PiStateBinding<GeneratedComplexState> schemaBinding = PiSchemas.require(GeneratedComplexState.class);
        PiPacketBinding<TestNoticePacket, ?> packetBinding = PiPackets.require(TestNoticePacket.class);

        assertSame(schemaBinding, PiSchemas.require(GENERATED_COMPLEX_STATE_ID));
        assertSame(packetBinding, PiPackets.require(TEST_NOTICE_PACKET_ID));
    }

    @Test
    void consumerRoundTripsGeneratedSchemaAndPacketWithinScopedSerializerRuntime() {
        PiSerializeRuntime runtime = runtime();

        PiSerializeServices.withScope(runtime, () -> {
            assertSame(runtime.require(PiSerializers.STRING), PiSerializeServices.requireSerializer(PiSerializers.STRING));

            PiStateBinding<GeneratedComplexState> schemaBinding = PiSchemas.require(GeneratedComplexState.class);
            GeneratedComplexState sourceState = new GeneratedComplexState();
            sourceState.names.add("alice");
            sourceState.counts.put("iron", 3);
            sourceState.child.value = 9;
            sourceState.label = "  boss  ";

            GeneratedComplexState restoredState = new GeneratedComplexState();
            restoredState.names.add("stale");
            restoredState.counts.put("old", 1);
            restoredState.child.value = 1;
            restoredState.label = "fallback";

            PiDecodeContext stateContext = PiDecodeContext.strict();
            schemaBinding.loadFull(restoredState, schemaBinding.saveFull(sourceState), stateContext);

            assertEquals(List.of("alice"), restoredState.names);
            assertEquals(Map.of("iron", 3), restoredState.counts);
            assertEquals(9, restoredState.child.value);
            assertEquals("boss", restoredState.label);
            assertTrue(stateContext.result().issues().isEmpty());

            PiPacketBinding<TestNoticePacket, ?> packetBinding = PiPackets.require(TestNoticePacket.class);
            TestNoticePacket sourcePacket = new TestNoticePacket("alert", List.of("alpha", "beta"));
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            packetBinding.codec().write(buffer, sourcePacket);
            TestNoticePacket restoredPacket = packetBinding.codec().read(buffer);

            assertEquals("alert", restoredPacket.title);
            assertEquals(List.of("alpha", "beta"), restoredPacket.lines);
        });
    }

    private static PiSerializeRuntime runtime() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        return runtime;
    }
}
