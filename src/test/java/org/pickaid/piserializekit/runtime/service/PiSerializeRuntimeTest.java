package org.pickaid.piserializekit.runtime.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.PiSchemaSerializers;
import org.pickaid.piserializekit.runtime.schema.TestSchemaProvider;

class PiSerializeRuntimeTest {
    @BeforeAll
    static void markMinecraftBootstrapReady() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            if (!field.getBoolean(null)) {
                // Forge's full bootstrap path initializes network internals that are unavailable in this unit harness.
                // For serializer tests we only need vanilla registries to be allowed to initialize lazily.
                field.setBoolean(null, true);
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to mark Minecraft bootstrap as initialized for test", exception);
        }
    }

    @Test
    void builtInsRegisterIntoRuntimeService() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertTrue(runtime.lookup(PiSerializers.INT).isPresent());
        assertTrue(runtime.lookup(PiSerializers.BYTE).isPresent());
        assertTrue(runtime.lookup(PiSerializers.SHORT).isPresent());
        assertTrue(runtime.lookup(PiSerializers.STRING).isPresent());
        assertTrue(runtime.lookup(PiSerializers.FLOAT).isPresent());
        assertTrue(runtime.lookup(PiSerializers.DOUBLE).isPresent());
        assertTrue(runtime.lookup(PiSerializers.UUID).isPresent());
        assertTrue(runtime.lookup(PiSerializers.RESOURCE_LOCATION).isPresent());
        assertTrue(runtime.lookup(PiSerializers.BLOCK_POS).isPresent());
        assertTrue(runtime.lookup(PiSerializers.VEC3).isPresent());
        assertTrue(runtime.lookup(PiSerializers.ITEM_STACK).isPresent());
    }

    @Test
    void builtInsCoverBlockPosVec3ItemStackAndEnumRoundTrips() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var byteSerializer = runtime.lookup(PiSerializers.BYTE).orElseThrow();
        var shortSerializer = runtime.lookup(PiSerializers.SHORT).orElseThrow();
        var floatSerializer = runtime.lookup(PiSerializers.FLOAT).orElseThrow();
        var doubleSerializer = runtime.lookup(PiSerializers.DOUBLE).orElseThrow();
        var blockPosSerializer = runtime.lookup(PiSerializers.BLOCK_POS).orElseThrow();
        var vec3Serializer = runtime.lookup(PiSerializers.VEC3).orElseThrow();
        var itemStackSerializer = runtime.lookup(PiSerializers.ITEM_STACK).orElseThrow();
        var raritySerializer = PiSerializers.enumType(Rarity.class);

        BlockPos pos = new BlockPos(1, 64, -2);
        Vec3 vec = new Vec3(1.5D, -0.25D, 3.75D);
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.getOrCreateTag().putString("note", "shiny");

        assertEquals((byte) 7, byteSerializer.nbtCodec().decode(byteSerializer.nbtCodec().encode((byte) 7)));
        assertEquals((short) 42, shortSerializer.packetCodec().read(writeAndFlip(shortSerializer.packetCodec(), (short) 42)));
        assertEquals(1.25F, floatSerializer.nbtCodec().decode(floatSerializer.nbtCodec().encode(1.25F)));
        assertEquals(-3.5D, doubleSerializer.packetCodec().read(writeAndFlip(doubleSerializer.packetCodec(), -3.5D)));
        assertEquals(pos, blockPosSerializer.nbtCodec().decode(blockPosSerializer.nbtCodec().encode(pos)));
        assertEquals(vec, vec3Serializer.nbtCodec().decode(vec3Serializer.nbtCodec().encode(vec)));
        assertEquals(vec, vec3Serializer.packetCodec().read(writeAndFlip(vec3Serializer.packetCodec(), vec)));

        ItemStack decodedStack = itemStackSerializer.nbtCodec().decode(itemStackSerializer.nbtCodec().encode(stack));
        assertTrue(ItemStack.matches(stack, decodedStack));
        assertEquals(stack.getCount(), decodedStack.getCount());
        assertTrue(ItemStack.matches(stack, itemStackSerializer.packetCodec().read(writeAndFlip(itemStackSerializer.packetCodec(), stack))));

        assertEquals(Rarity.RARE, raritySerializer.packetCodec().read(writeAndFlip(raritySerializer.packetCodec(), Rarity.RARE)));
        assertEquals(Rarity.RARE, raritySerializer.nbtCodec().decode(raritySerializer.nbtCodec().encode(Rarity.RARE)));
    }

    @Test
    void itemStackSerializerRejectsUnknownItemIds() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var itemStackSerializer = runtime.lookup(PiSerializers.ITEM_STACK).orElseThrow();
        CompoundTag invalidStack = new CompoundTag();
        invalidStack.putString("id", "test:missing_item");
        invalidStack.putByte("Count", (byte) 1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> itemStackSerializer.nbtCodec().decode(invalidStack)
        );
        assertTrue(exception.getMessage().contains("Unknown item id for ItemStack"));
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
        var arraySerializer = PiSerializers.arrayOf(ResourceLocation[].class, resourceLocationSerializer);

        List<ResourceLocation> locations = List.of(
                ResourceLocation.fromNamespaceAndPath("test", "one"),
                ResourceLocation.fromNamespaceAndPath("test", "two")
        );
        ResourceLocation[] locationArray = locations.toArray(ResourceLocation[]::new);
        Set<String> names = Set.of("alice", "bob");
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("iron", 3);
        counts.put("gold", 7);
        Optional<UUID> runId = Optional.of(UUID.fromString("123e4567-e89b-12d3-a456-426614174099"));

        FriendlyByteBuf locationBuffer = new FriendlyByteBuf(Unpooled.buffer());
        listSerializer.packetCodec().write(locationBuffer, locations);
        assertEquals(locations, listSerializer.packetCodec().read(locationBuffer));

        FriendlyByteBuf locationArrayBuffer = new FriendlyByteBuf(Unpooled.buffer());
        arraySerializer.packetCodec().write(locationArrayBuffer, locationArray);
        assertArrayEquals(locationArray, arraySerializer.packetCodec().read(locationArrayBuffer));

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
        assertArrayEquals(locationArray, arraySerializer.nbtCodec().decode(arraySerializer.nbtCodec().encode(locationArray)));
        assertEquals(names, setSerializer.nbtCodec().decode(setSerializer.nbtCodec().encode(names)));
        assertEquals(counts, mapSerializer.nbtCodec().decode(mapSerializer.nbtCodec().encode(counts)));
        assertEquals(runId, optionalSerializer.nbtCodec().decode(optionalSerializer.nbtCodec().encode(runId)));
    }

    @Test
    void schemaBackedSerializerRoundTripsRegisteredStateBindings() {
        TestSchemaProvider.TestState state = new TestSchemaProvider.TestState();
        state.value = 37;

        var serializer = PiSchemaSerializers.forState(TestSchemaProvider.TestState.class);

        TestSchemaProvider.TestState nbtDecoded = serializer.nbtCodec().decode(serializer.nbtCodec().encode(state));
        assertEquals(37, nbtDecoded.value);

        TestSchemaProvider.TestState packetDecoded = serializer.packetCodec().read(writeAndFlip(serializer.packetCodec(), state));
        assertEquals(37, packetDecoded.value);
    }

    @Test
    void lookupAcceptsPrimitiveClassTokensForBuiltInNumericSerializers() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertTrue(runtime.lookup(PiSerializers.INT.id(), int.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.LONG.id(), long.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.BYTE.id(), byte.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.SHORT.id(), short.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.FLOAT.id(), float.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.DOUBLE.id(), double.class).isPresent());
        assertTrue(runtime.lookup(PiSerializers.BOOLEAN.id(), boolean.class).isPresent());
    }

    @Test
    void lookupRejectsMismatchedJavaType() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertFalse(runtime.lookup(PiSerializers.INT.id(), String.class).isPresent());
    }

    private static <T> FriendlyByteBuf writeAndFlip(PiPacketCodec<T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.write(buffer, value);
        buffer.readerIndex(0);
        return buffer;
    }
}
