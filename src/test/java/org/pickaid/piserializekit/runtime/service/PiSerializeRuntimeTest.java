package org.pickaid.piserializekit.runtime.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import org.pickaid.piserializekit.api.packet.PiPacketCodecDecodeException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializerType;
import org.pickaid.piserializekit.api.service.PiSerializeServices;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaSerializers;
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
        assertTrue(runtime.serializerIds().contains(PiSerializers.INT.id()));
        assertTrue(runtime.serializerJavaTypes().contains(Integer.class));
    }

    @Test
    void runtimeExposesKnownSerializerIdsAndJavaTypesInStableOrder() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        List<ResourceLocation> ids = runtime.serializerIds();
        List<Class<?>> javaTypes = runtime.serializerJavaTypes();

        assertTrue(ids.contains(PiSerializers.INT.id()));
        assertTrue(ids.contains(PiSerializers.STRING.id()));
        assertTrue(javaTypes.contains(Integer.class));
        assertTrue(javaTypes.contains(String.class));
        assertEquals(ids.stream().map(ResourceLocation::toString).sorted().toList(), ids.stream().map(ResourceLocation::toString).toList());
        assertEquals(javaTypes.stream().map(Class::getName).sorted().toList(), javaTypes.stream().map(Class::getName).toList());
    }

    @Test
    void duplicateSerializerRegistrationReportsBothJavaTypes() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> {
                    runtime.register(
                            new ResourceLocation("test", "shared"),
                            Integer.class,
                            runtime.lookup(PiSerializers.INT).orElseThrow()
                    );
                    runtime.register(
                            new ResourceLocation("test", "shared"),
                            String.class,
                            runtime.lookup(PiSerializers.STRING).orElseThrow()
                    );
                }
        );

        assertEquals(
                "Duplicate Pi serializer registration for test:shared; existing java type java.lang.Integer, conflicting java type java.lang.String",
                exception.getMessage()
        );
        assertEquals("serializer-id", exception.category());
        assertEquals("test:shared", exception.key());
    }

    @Test
    void duplicateRegistrationOfSameSerializerInstanceIsIdempotent() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        PiSerializer<Integer> serializer = runtime.lookup(PiSerializers.INT).orElseThrow();

        runtime.register(PiSerializers.INT, serializer);

        assertSame(serializer, runtime.require(PiSerializers.INT));
    }

    @Test
    void duplicateRegistrationForSameIdAndTypeRejectsConflictingSerializerWithOverrideGuidance() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        ResourceLocation sharedId = new ResourceLocation("test", "shared_string");
        PiSerializer<String> first = constantStringSerializer("first");
        PiSerializer<String> second = constantStringSerializer("second");

        runtime.register(sharedId, String.class, first);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> runtime.register(sharedId, String.class, second)
        );

        assertEquals(
                "Duplicate Pi serializer registration for test:shared_string; java type java.lang.String is already registered. "
                        + "Use PiSerializeServices.withScope(...) for overrides instead of re-registering the same id and type.",
                exception.getMessage()
        );
    }

    @Test
    void builtInInstallIsIdempotentAndDoesNotClobberExistingMatchingRegistration() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiSerializer<Integer> customInt = constantIntSerializer(77);

        runtime.register(PiSerializers.INT, customInt);
        PiBuiltInSerializers.install(runtime);
        PiBuiltInSerializers.install(runtime);

        assertSame(customInt, runtime.require(PiSerializers.INT));
        assertTrue(runtime.lookup(PiSerializers.STRING).isPresent());
    }

    @Test
    void requireSerializerReportsKnownIdsWhenSerializerIsMissing() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        PiSerializerType<String> missingType = new PiSerializerType<>(new ResourceLocation("test", "missing_string"), String.class);

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> PiSerializeServices.withScope(runtime, () -> PiSerializeServices.requireSerializer(missingType))
        );

        assertTrue(exception.getMessage().contains("Missing Pi serializer for test:missing_string / java.lang.String"));
        assertTrue(exception.getMessage().contains("known serializer ids:"));
        assertTrue(exception.getMessage().contains(PiSerializers.BLOCK_POS.id().toString()));
    }

    @Test
    void emptyRuntimeMissingSerializerMessageIncludesBootstrapHint() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiSerializerType<String> missingType = new PiSerializerType<>(new ResourceLocation("test", "missing_string"), String.class);

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> runtime.require(missingType)
        );

        assertEquals(
                "Missing Pi serializer for test:missing_string / java.lang.String; known serializer ids: <none>; serializer runtime is empty. Install built-ins or register serializers before resolving author-facing codec ids.",
                exception.getMessage()
        );
    }

    @Test
    void requireSerializerReportsRegisteredJavaTypeWhenIdExistsWithDifferentType() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        ResourceLocation sharedId = new ResourceLocation("test", "shared");
        runtime.register(sharedId, Integer.class, runtime.lookup(PiSerializers.INT).orElseThrow());

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> PiSerializeServices.withScope(runtime, () -> PiSerializeServices.requireSerializer(new PiSerializerType<>(sharedId, String.class)))
        );

        assertEquals(
                "Missing Pi serializer for test:shared / java.lang.String; serializer id is registered with java type java.lang.Integer",
                exception.getMessage()
        );
        assertEquals("serializer-id", exception.category());
        assertEquals("test:shared", exception.key());
    }

    @Test
    void serviceConvenienceLookupAndRequireOverloadsWorkForIdAndType() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertTrue(runtime.lookup(PiSerializers.INT.id(), Integer.class).isPresent());
        assertSame(runtime.lookup(PiSerializers.INT).orElseThrow(), runtime.require(PiSerializers.INT.id(), Integer.class));

        PiSerializeServices.withScope(runtime, () -> {
            assertTrue(PiSerializeServices.findSerializer(PiSerializers.INT).isPresent());
            assertTrue(PiSerializeServices.findSerializer(PiSerializers.INT.id(), Integer.class).isPresent());
            assertSame(
                    PiSerializeServices.findSerializer(PiSerializers.INT).orElseThrow(),
                    PiSerializeServices.requireSerializer(PiSerializers.INT.id(), Integer.class)
            );
        });
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
                new ResourceLocation("test", "one"),
                new ResourceLocation("test", "two")
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
    void collectionBuildersDecodeRawListPayloadsAndLegacyWrappers() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var stringSerializer = runtime.lookup(PiSerializers.STRING).orElseThrow();
        var intSerializer = runtime.lookup(PiSerializers.INT).orElseThrow();
        var resourceLocationSerializer = runtime.lookup(PiSerializers.RESOURCE_LOCATION).orElseThrow();

        var listSerializer = PiSerializers.listOf(resourceLocationSerializer);
        var setSerializer = PiSerializers.setOf(stringSerializer);
        var mapSerializer = PiSerializers.mapOf(stringSerializer, intSerializer);
        var arraySerializer = PiSerializers.arrayOf(ResourceLocation[].class, resourceLocationSerializer);

        List<ResourceLocation> locations = List.of(
                new ResourceLocation("test", "one"),
                new ResourceLocation("test", "two")
        );
        Set<String> names = new LinkedHashSet<>(List.of("alice", "bob"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("iron", 3);
        counts.put("gold", 7);
        ResourceLocation[] locationArray = locations.toArray(ResourceLocation[]::new);

        CompoundTag encodedList = listSerializer.nbtCodec().encode(locations);
        Tag rawListPayload = encodedList.get("__pi_value");
        assertTrue(rawListPayload instanceof ListTag);
        assertEquals(locations, listSerializer.nbtCodec().decodeTag(rawListPayload));
        assertEquals(locations, listSerializer.nbtCodec().decodeTag(encodedList));

        CompoundTag encodedSet = setSerializer.nbtCodec().encode(names);
        Tag rawSetPayload = encodedSet.get("__pi_value");
        assertTrue(rawSetPayload instanceof ListTag);
        assertEquals(names, setSerializer.nbtCodec().decodeTag(rawSetPayload));
        assertEquals(names, setSerializer.nbtCodec().decodeTag(encodedSet));

        CompoundTag encodedMap = mapSerializer.nbtCodec().encode(counts);
        assertEquals(counts, mapSerializer.nbtCodec().decodeTag(encodedMap));
        CompoundTag legacyWrappedMap = new CompoundTag();
        legacyWrappedMap.put("__pi_value", encodedMap.copy());
        assertEquals(counts, mapSerializer.nbtCodec().decodeTag(legacyWrappedMap));

        CompoundTag encodedArray = arraySerializer.nbtCodec().encode(locationArray);
        Tag rawArrayPayload = encodedArray.get("__pi_value");
        assertTrue(rawArrayPayload instanceof ListTag);
        assertArrayEquals(locationArray, arraySerializer.nbtCodec().decodeTag(rawArrayPayload));
        assertArrayEquals(locationArray, arraySerializer.nbtCodec().decodeTag(encodedArray));
    }

    @Test
    void collectionBuildersDecodeIntoReuseMutableTargets() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var stringSerializer = runtime.lookup(PiSerializers.STRING).orElseThrow();
        var intSerializer = runtime.lookup(PiSerializers.INT).orElseThrow();
        var resourceLocationSerializer = runtime.lookup(PiSerializers.RESOURCE_LOCATION).orElseThrow();

        var listSerializer = PiSerializers.listOf(resourceLocationSerializer);
        var setSerializer = PiSerializers.setOf(stringSerializer);
        var mapSerializer = PiSerializers.mapOf(stringSerializer, intSerializer);

        CompoundTag encodedList = listSerializer.nbtCodec().encode(List.of(
                new ResourceLocation("test", "one"),
                new ResourceLocation("test", "two")
        ));
        CompoundTag encodedSet = setSerializer.nbtCodec().encode(new LinkedHashSet<>(List.of("alice", "bob")));
        Map<String, Integer> encodedCounts = new LinkedHashMap<>();
        encodedCounts.put("iron", 3);
        encodedCounts.put("gold", 7);
        CompoundTag encodedMap = mapSerializer.nbtCodec().encode(encodedCounts);

        List<ResourceLocation> listTarget = new ArrayList<>(List.of(new ResourceLocation("stale", "entry")));
        Set<String> setTarget = new LinkedHashSet<>(List.of("stale"));
        Map<String, Integer> mapTarget = new LinkedHashMap<>(Map.of("stale", 99));

        assertSame(listTarget, listSerializer.nbtCodec().decodeInto(encodedList, listTarget));
        assertEquals(List.of(
                new ResourceLocation("test", "one"),
                new ResourceLocation("test", "two")
        ), listTarget);

        assertSame(setTarget, setSerializer.nbtCodec().decodeInto(encodedSet, setTarget));
        assertEquals(new LinkedHashSet<>(List.of("alice", "bob")), setTarget);

        assertSame(mapTarget, mapSerializer.nbtCodec().decodeInto(encodedMap, mapTarget));
        assertEquals(encodedCounts, mapTarget);
    }

    @Test
    void collectionPacketDecodeSkipsFailedNestedElementsAndCollectsStructuredIssues() {
        PiSerializer<String> throwing = throwingPacketStringSerializer("element boom");

        var listSerializer = PiSerializers.listOf(throwing);
        FriendlyByteBuf listBuffer = new FriendlyByteBuf(Unpooled.buffer());
        listBuffer.writeVarInt(1);
        PiDecodeContext listContext = PiDecodeContext.strict();
        List<String> decodedList = listSerializer.packetCodec().read(listBuffer, listContext);

        assertTrue(decodedList.isEmpty());
        assertTrue(listContext.result().hasFatal());
        assertEquals("[0]", listContext.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, listContext.result().issues().get(0).code());
        assertEquals("element boom", listContext.result().issues().get(0).message());

        var setSerializer = PiSerializers.setOf(throwing);
        FriendlyByteBuf setBuffer = new FriendlyByteBuf(Unpooled.buffer());
        setBuffer.writeVarInt(1);
        PiDecodeContext setContext = PiDecodeContext.strict();
        Set<String> decodedSet = setSerializer.packetCodec().read(setBuffer, setContext);

        assertTrue(decodedSet.isEmpty());
        assertTrue(setContext.result().hasFatal());
        assertEquals("[0]", setContext.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, setContext.result().issues().get(0).code());
        assertEquals("element boom", setContext.result().issues().get(0).message());

        var arraySerializer = PiSerializers.arrayOf(String[].class, throwing);
        FriendlyByteBuf arrayBuffer = new FriendlyByteBuf(Unpooled.buffer());
        arrayBuffer.writeVarInt(1);
        PiDecodeContext arrayContext = PiDecodeContext.strict();
        String[] decodedArray = arraySerializer.packetCodec().read(arrayBuffer, arrayContext);

        assertArrayEquals(new String[0], decodedArray);
        assertTrue(arrayContext.result().hasFatal());
        assertEquals("[0]", arrayContext.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, arrayContext.result().issues().get(0).code());
        assertEquals("element boom", arrayContext.result().issues().get(0).message());
    }

    @Test
    void optionalPacketDecodeTurnsFailedNestedPayloadIntoEmptyOptional() {
        PiSerializer<String> throwing = throwingPacketStringSerializer("optional boom");
        var optionalSerializer = PiSerializers.optionalOf(throwing);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(true);

        PiDecodeContext context = PiDecodeContext.strict();
        Optional<String> decoded = optionalSerializer.packetCodec().read(buffer, context);

        assertEquals(Optional.empty(), decoded);
        assertTrue(context.result().hasFatal());
        assertEquals("value", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("optional boom", context.result().issues().get(0).message());
    }

    @Test
    void mapPacketDecodeSkipsEntriesWithFailedNestedKeyOrValue() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        var stringSerializer = runtime.lookup(PiSerializers.STRING).orElseThrow();
        PiSerializer<String> throwingKey = throwingPacketStringSerializer("key boom");
        PiSerializer<String> throwingValue = throwingPacketStringSerializer("value boom");

        var keyFailureSerializer = PiSerializers.mapOf(throwingKey, stringSerializer);
        FriendlyByteBuf keyFailureBuffer = new FriendlyByteBuf(Unpooled.buffer());
        keyFailureBuffer.writeVarInt(1);
        keyFailureBuffer.writeUtf("decoded-value");
        PiDecodeContext keyFailureContext = PiDecodeContext.strict();
        Map<String, String> keyFailureDecoded = keyFailureSerializer.packetCodec().read(keyFailureBuffer, keyFailureContext);

        assertTrue(keyFailureDecoded.isEmpty());
        assertTrue(keyFailureContext.result().hasFatal());
        assertEquals("[0].key", keyFailureContext.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, keyFailureContext.result().issues().get(0).code());
        assertEquals("key boom", keyFailureContext.result().issues().get(0).message());

        var valueFailureSerializer = PiSerializers.mapOf(stringSerializer, throwingValue);
        FriendlyByteBuf valueFailureBuffer = new FriendlyByteBuf(Unpooled.buffer());
        valueFailureBuffer.writeVarInt(1);
        valueFailureBuffer.writeUtf("decoded-key");
        PiDecodeContext valueFailureContext = PiDecodeContext.strict();
        Map<String, String> valueFailureDecoded = valueFailureSerializer.packetCodec().read(valueFailureBuffer, valueFailureContext);

        assertTrue(valueFailureDecoded.isEmpty());
        assertTrue(valueFailureContext.result().hasFatal());
        assertEquals("[0].value", valueFailureContext.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, valueFailureContext.result().issues().get(0).code());
        assertEquals("value boom", valueFailureContext.result().issues().get(0).message());
    }

    @Test
    void strictCollectionPacketDecodeThrowsStructuredExceptionForNestedFailures() {
        PiSerializer<String> throwing = throwingPacketStringSerializer("strict boom");
        var listSerializer = PiSerializers.listOf(throwing);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);

        PiPacketCodecDecodeException exception = assertThrows(
                PiPacketCodecDecodeException.class,
                () -> listSerializer.packetCodec().read(buffer)
        );

        assertTrue(exception.result().hasFatal());
        assertEquals("[0]", exception.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, exception.result().issues().get(0).code());
        assertEquals("strict boom", exception.result().issues().get(0).message());
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
    void schemaBackedSerializerDecodeIntoReusesExistingState() {
        TestSchemaProvider.TestState encoded = new TestSchemaProvider.TestState();
        encoded.value = 37;
        TestSchemaProvider.TestState target = new TestSchemaProvider.TestState();
        target.value = 5;

        var serializer = PiSchemaSerializers.forState(TestSchemaProvider.TestState.class);

        assertSame(target, serializer.nbtCodec().decodeInto(serializer.nbtCodec().encode(encoded), target));
        assertEquals(37, target.value);
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

    @Test
    void serializeServicesExposeKnownIdsAndTypesFromScopedRuntime() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSerializeServices.withScope(runtime, () -> {
            assertTrue(PiSerializeServices.serializerIds().contains(PiSerializers.INT.id()));
            assertTrue(PiSerializeServices.serializerJavaTypes().contains(Integer.class));
        });
    }

    private static <T> FriendlyByteBuf writeAndFlip(PiPacketCodec<T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.write(buffer, value);
        buffer.readerIndex(0);
        return buffer;
    }

    private static PiSerializer<String> throwingPacketStringSerializer(String message) {
        return PiSerializers.of(Codec.STRING, new PiPacketCodec<>() {
            @Override
            public void write(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, String value) {
                buffer.writeUtf(value);
            }

            @Override
            public String read(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, PiDecodeContext context) {
                throw new IllegalStateException(message);
            }
        });
    }

    private static PiSerializer<String> constantStringSerializer(String fallback) {
        return PiSerializers.of(Codec.STRING, new PiPacketCodec<>() {
            @Override
            public void write(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, String value) {
                buffer.writeUtf(value);
            }

            @Override
            public String read(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, PiDecodeContext context) {
                return fallback;
            }
        });
    }

    private static PiSerializer<Integer> constantIntSerializer(int fallback) {
        return PiSerializers.of(Codec.INT, new PiPacketCodec<>() {
            @Override
            public void write(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, Integer value) {
                buffer.writeVarInt(value);
            }

            @Override
            public Integer read(org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer buffer, PiDecodeContext context) {
                return fallback;
            }
        });
    }
}
