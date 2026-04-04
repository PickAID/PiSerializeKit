package org.pickaid.piserializekit.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.service.PiSerializers;

class PiSerializeRuntimeTest {
    @Test
    void builtInsRegisterIntoRuntimeService() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertTrue(runtime.lookup(PiSerializers.INT).isPresent());
        assertTrue(runtime.lookup(PiSerializers.STRING).isPresent());
        assertTrue(runtime.lookup(PiSerializers.UUID).isPresent());
        assertTrue(runtime.lookup(PiSerializers.RESOURCE_LOCATION).isPresent());
    }

    @Test
    void collectionBuildersRoundTripThroughPacketAndNbt() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var stringSerializer = runtime.lookup(PiSerializers.STRING).orElseThrow();
        var intSerializer = runtime.lookup(PiSerializers.INT).orElseThrow();
        var uuidSerializer = runtime.lookup(PiSerializers.UUID).orElseThrow();
        var resourceLocationSerializer = runtime.lookup(PiSerializers.RESOURCE_LOCATION).orElseThrow();

        var listSerializer = PiSerializers.listOf(resourceLocationSerializer);
        var setSerializer = PiSerializers.setOf(stringSerializer);
        var mapSerializer = PiSerializers.mapOf(stringSerializer, intSerializer);
        var optionalSerializer = PiSerializers.optionalOf(uuidSerializer);

        List<ResourceLocation> locations = List.of(
                ResourceLocation.fromNamespaceAndPath("test", "one"),
                ResourceLocation.fromNamespaceAndPath("test", "two")
        );
        Set<String> names = Set.of("alice", "bob");
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("iron", 3);
        counts.put("gold", 7);
        Optional<UUID> runId = Optional.of(UUID.fromString("123e4567-e89b-12d3-a456-426614174099"));

        FriendlyByteBuf locationBuffer = new FriendlyByteBuf(Unpooled.buffer());
        listSerializer.packetCodec().write(locationBuffer, locations);
        assertEquals(locations, listSerializer.packetCodec().read(locationBuffer));

        FriendlyByteBuf nameBuffer = new FriendlyByteBuf(Unpooled.buffer());
        setSerializer.packetCodec().write(nameBuffer, names);
        assertEquals(names, setSerializer.packetCodec().read(nameBuffer));

        FriendlyByteBuf mapBuffer = new FriendlyByteBuf(Unpooled.buffer());
        mapSerializer.packetCodec().write(mapBuffer, counts);
        assertEquals(counts, mapSerializer.packetCodec().read(mapBuffer));

        FriendlyByteBuf optionalBuffer = new FriendlyByteBuf(Unpooled.buffer());
        optionalSerializer.packetCodec().write(optionalBuffer, runId);
        assertEquals(runId, optionalSerializer.packetCodec().read(optionalBuffer));

        assertEquals(locations, listSerializer.nbtCodec().decode(listSerializer.nbtCodec().encode(locations)));
        assertEquals(names, setSerializer.nbtCodec().decode(setSerializer.nbtCodec().encode(names)));
        assertEquals(counts, mapSerializer.nbtCodec().decode(mapSerializer.nbtCodec().encode(counts)));
        assertEquals(runId, optionalSerializer.nbtCodec().decode(optionalSerializer.nbtCodec().encode(runId)));
    }
}
