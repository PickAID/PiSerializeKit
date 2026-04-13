package org.pickaid.piserializekit.runtime.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.packet.PiPacketDirection;
import org.pickaid.piserializekit.api.packet.PiPacketRegistry;
import org.pickaid.piserializekit.api.packet.PiServerPacketContext;
import org.pickaid.piserializekit.api.packet.PiPacketDecodeException;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.runtime.packet.fixture.TestNoticePacket;
import org.pickaid.piserializekit.runtime.packet.fixture.ThrowingCtorPacket;
import org.pickaid.piserializekit.runtime.packet.fixture.ThrowingNoticePacket;

class PiPacketsTest {
    @Test
    void packetBindingsLoadByTypeAndPacketId() {
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "test_notice"), binding.packetId());
        assertSame(binding, PiPackets.require(ResourceLocation.fromNamespaceAndPath("test", "test_notice")));
        assertTrue(PiPackets.packetIds().contains(ResourceLocation.fromNamespaceAndPath("test", "test_notice")));
        assertTrue(PiPackets.packetTypes().contains(TestNoticePacket.class));
    }

    @Test
    void packetRegistryExposesKnownPacketIdsAndTypes() throws Exception {
        PiPacketRegistry registry = newRegistry();
        registry.register(TestNoticePacket.class, PiPackets.require(TestNoticePacket.class));

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("test", "test_notice")), registry.packetIds());
        assertEquals(List.of(TestNoticePacket.class), registry.packetTypes());
    }

    @Test
    void rejectsDuplicateBindingsForSamePacketTypeWithConflictDetails() throws Exception {
        PiPacketRegistry registry = newRegistry();
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);
        registry.register(TestNoticePacket.class, binding);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> registry.register(TestNoticePacket.class, new DuplicatePacketTypeBinding())
        );

        assertEquals(
                "Duplicate Pi packet binding for type " + TestNoticePacket.class.getName()
                        + "; existing packet id test:test_notice, conflicting packet id test:duplicate_packet_type",
                exception.getMessage()
        );
    }

    @Test
    void rejectsDuplicateBindingsForSamePacketIdWithConflictDetails() throws Exception {
        PiPacketRegistry registry = newRegistry();
        PiPacketBinding<TestNoticePacket, ?> binding = PiPackets.require(TestNoticePacket.class);
        registry.register(TestNoticePacket.class, binding);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> registry.register(OtherPacket.class, new DuplicatePacketIdBinding())
        );

        assertEquals(
                "Duplicate Pi packet binding for id test:test_notice"
                        + "; existing packet type " + TestNoticePacket.class.getName()
                        + ", conflicting packet type " + OtherPacket.class.getName(),
                exception.getMessage()
        );
        assertEquals("packet-id", exception.category());
        assertEquals("test:test_notice", exception.key());
    }

    @Test
    void missingPacketBindingReportsKnownPacketIdsForTypeLookup() throws Exception {
        PiPacketRegistry registry = newRegistry();
        registry.register(TestNoticePacket.class, PiPackets.require(TestNoticePacket.class));

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(OtherPacket.class)
        );

        assertEquals(
                "Missing Pi packet binding for " + OtherPacket.class.getName() + "; known packet ids: test:test_notice",
                exception.getMessage()
        );
    }

    @Test
    void missingPacketBindingReportsKnownPacketIdsForIdLookup() throws Exception {
        PiPacketRegistry registry = newRegistry();
        registry.register(TestNoticePacket.class, PiPackets.require(TestNoticePacket.class));

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(ResourceLocation.fromNamespaceAndPath("test", "missing"))
        );

        assertEquals(
                "Missing Pi packet binding for test:missing; known packet ids: test:test_notice",
                exception.getMessage()
        );
        assertEquals("packet-id", exception.category());
        assertEquals("test:missing", exception.key());
    }

    @Test
    void missingPacketBindingOnEmptyRegistryIncludesProviderHint() throws Exception {
        PiPacketRegistry registry = newRegistry();

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(OtherPacket.class)
        );

        assertEquals(
                "Missing Pi packet binding for " + OtherPacket.class.getName()
                        + "; known packet ids: <none>; no PiPacketProvider entries were loaded. Ensure annotation processing generated packet providers and META-INF/services resources are on the runtime classpath.",
                exception.getMessage()
        );
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

    @Test
    void generatedPacketCurrentVersionDecodeCollectsThrownFieldCodecFailure() {
        PiPacketBinding<ThrowingNoticePacket, ?> binding = PiPackets.require(ThrowingNoticePacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);

        PiDecodeContext context = PiDecodeContext.strict();
        ThrowingNoticePacket decoded = binding.codec().read(buffer, context);

        assertEquals("", decoded.title);
        assertTrue(context.result().hasFatal());
        assertEquals("title", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("IllegalStateException", context.result().issues().get(0).message());
    }

    @Test
    void generatedPacketStrictReadWrapsThrownFieldCodecFailureIntoStructuredPacketException() {
        PiPacketBinding<ThrowingNoticePacket, ?> binding = PiPackets.require(ThrowingNoticePacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);

        PiPacketDecodeException exception = assertThrows(PiPacketDecodeException.class, () -> binding.codec().read(buffer));

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "throwing_notice"), exception.packetId());
        assertTrue(exception.result().hasFatal());
        assertEquals("title", exception.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, exception.result().issues().get(0).code());
        assertEquals("IllegalStateException", exception.result().issues().get(0).message());
    }

    @Test
    void generatedPacketStrictReadWrapsThrownConstructorFailureIntoStructuredPacketException() {
        PiPacketBinding<ThrowingCtorPacket, ?> binding = PiPackets.require(ThrowingCtorPacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("alert");

        PiPacketDecodeException exception = assertThrows(PiPacketDecodeException.class, () -> binding.codec().read(buffer));

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "throwing_ctor"), exception.packetId());
        assertTrue(exception.result().hasFatal());
        assertEquals("$", exception.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, exception.result().issues().get(0).code());
        assertEquals("packet construction failed: ctor boom", exception.result().issues().get(0).message());
    }

    @Test
    void generatedPacketContextReadCollectsThrownConstructorFailureWithoutLeakingRuntimeException() {
        PiPacketBinding<ThrowingCtorPacket, ?> binding = PiPackets.require(ThrowingCtorPacket.class);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("alert");

        PiDecodeContext context = PiDecodeContext.strict();
        ThrowingCtorPacket decoded = binding.codec().read(buffer, context);

        assertEquals(null, decoded);
        assertTrue(context.result().hasFatal());
        assertEquals("$", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("packet construction failed: ctor boom", context.result().issues().get(0).message());
    }

    @Test
    void safeReadUsesExceptionTypeFallbackWhenMessageIsBlank() {
        PiDecodeContext context = PiDecodeContext.strict();

        int value = PiPacketSupport.safeRead(context, "value", () -> {
            throw new IllegalStateException("   ");
        }, -1);

        assertEquals(-1, value);
        assertEquals(1, context.result().issues().size());
        assertEquals("value", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("packet decode failed: IllegalStateException", context.result().issues().get(0).message());
    }

    @Test
    void writePayloadFieldUsesExceptionTypeFallbackWhenMessageIsBlank() {
        CompoundTag payload = new CompoundTag();
        PiDecodeContext context = PiDecodeContext.strict();

        PiPacketSupport.writePayloadField(payload, "value", blankEncodeMessageSerializer(), "boom", context);

        assertTrue(!payload.contains("value"));
        assertEquals(1, context.result().issues().size());
        assertEquals("value", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("failed to encode packet payload field: IllegalStateException", context.result().issues().get(0).message());
    }

    @Test
    void rejectsPacketBindingWithNonPositiveVersion() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new InvalidPacketVersionBinding())
        );

        assertEquals("Pi packet binding version must be >= 1 for test:invalid_packet_version", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithInvalidFieldIds() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new InvalidPacketFieldsBinding())
        );

        assertEquals("Pi packet field id must be a valid payload key in binding test:invalid_packet_fields: Count Value", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithReservedFieldPrefix() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new ReservedPacketFieldsBinding())
        );

        assertEquals("Pi packet field id uses reserved Pi payload prefix in binding test:reserved_packet_fields: __pi_value", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithSparseFieldIndexes() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new SparsePacketFieldsBinding())
        );

        assertEquals("Pi packet field indexes must cover [0..1] in binding test:sparse_packet_fields", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithDuplicateMigrationSources() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new InvalidPacketMigrationsBinding())
        );

        assertEquals(
                "Duplicate pi packet migration from version 1 in binding test:invalid_packet_migrations",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPacketBindingWithIncompleteMigrationChain() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new IncompletePacketMigrationsBinding())
        );

        assertEquals(
                "Pi packet migration chain must define a path from version 1 to 4 in binding test:incomplete_packet_migrations; "
                        + "missing step from version 2. Declared steps: 1->2, 3->4",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPacketBindingWithNullPacketTypeBeforeTypeMismatchPath() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketTypeBinding())
        );

        assertEquals("Pi packet binding packetType() must return a non-null class for test:null_packet_type", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullCodec() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketCodecBinding())
        );

        assertEquals("Pi packet binding codec() must return a non-null codec for test:null_packet_codec", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullFieldsList() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketFieldsBinding())
        );

        assertEquals("Pi packet binding fields() must return a non-null list for test:null_packet_fields", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullPacketId() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketIdBinding())
        );

        assertEquals(
                "Pi packet binding packetId() must return a non-null id for "
                        + NullPacketIdBinding.class.getName(),
                exception.getMessage()
        );
    }

    @Test
    void rejectsPacketBindingWithNullDirection() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketDirectionBinding())
        );

        assertEquals("Pi packet binding direction() must return a non-null direction for test:null_packet_direction", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullMigrationList() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketMigrationsBinding())
        );

        assertEquals("Pi packet binding migrations() must return a non-null list for test:null_packet_migrations", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullFieldEntry() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketFieldEntryBinding())
        );

        assertEquals("Pi packet binding fields() must not contain null entries for test:null_packet_field_entry", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithNullMigrationEntry() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(InvalidPacket.class, new NullPacketMigrationEntryBinding())
        );

        assertEquals("Pi packet binding migrations() must not contain null entries for test:null_packet_migration_entry", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithAbstractPacketType() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(AbstractPacket.class, new AbstractPacketBinding())
        );

        assertEquals("Pi packet binding packetType must be a concrete class for test:abstract_packet_binding", exception.getMessage());
    }

    @Test
    void rejectsPacketBindingWithDirectionMismatchAgainstServerPacketBaseType() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(DirectionMismatchServerPacket.class, new WrongDirectionServerPacketBinding())
        );

        assertEquals(
                "Pi packet binding direction CLIENTBOUND conflicts with packet base type "
                        + DirectionMismatchServerPacket.class.getName()
                        + " which implies SERVERBOUND",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPacketBindingWithDirectionMismatchAgainstClientPacketBaseType() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(DirectionMismatchClientPacket.class, new WrongDirectionClientPacketBinding())
        );

        assertEquals(
                "Pi packet binding direction SERVERBOUND conflicts with packet base type "
                        + DirectionMismatchClientPacket.class.getName()
                        + " which implies CLIENTBOUND",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPacketBindingWithDirectionMismatchAgainstBidirectionalPacketBaseType() throws Exception {
        PiPacketRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(DirectionMismatchBidirectionalPacket.class, new WrongDirectionBidirectionalPacketBinding())
        );

        assertEquals(
                "Pi packet binding direction CLIENTBOUND conflicts with packet base type "
                        + DirectionMismatchBidirectionalPacket.class.getName()
                        + " which implies BIDIRECTIONAL",
                exception.getMessage()
        );
    }

    private static PiPacketRegistry newRegistry() throws Exception {
        Class<?> registryType = Class.forName("org.pickaid.piserializekit.runtime.packet.PiPackets$ServiceLoaderRegistry");
        Constructor<?> constructor = registryType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object registry = constructor.newInstance();
        java.lang.reflect.Field loaded = registryType.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(registry, true);
        return (PiPacketRegistry) registry;
    }

    private static PiSerializer<String> blankEncodeMessageSerializer() {
        return new PiSerializer<>() {
            @Override
            public Codec<String> valueCodec() {
                return Codec.STRING;
            }

            @Override
            public PiNbtCodec<String> nbtCodec() {
                return new PiNbtCodec<>() {
                    @Override
                    public CompoundTag encode(String value) {
                        throw new IllegalStateException("   ");
                    }

                    @Override
                    public String decode(CompoundTag tag) {
                        return "";
                    }
                };
            }

            @Override
            public PiPacketCodec<String> packetCodec() {
                return new PiPacketCodec<>() {
                    @Override
                    public void write(FriendlyByteBuf buffer, String value) {
                    }

                    @Override
                    public String read(FriendlyByteBuf buffer, PiDecodeContext context) {
                        return "";
                    }
                };
            }
        };
    }

    private static final class InvalidPacket {
    }

    private static final class OtherPacket extends org.pickaid.piserializekit.api.packet.PiServerPacket {
        @Override
        protected void handle(PiServerPacketContext context) {
        }
    }

    private static final class DuplicatePacketTypeBinding implements PiPacketBinding<TestNoticePacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "duplicate_packet_type");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<TestNoticePacket> packetType() {
            return TestNoticePacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<TestNoticePacket> codec() {
            return noopCodec(new TestNoticePacket("", List.of()));
        }

        @Override
        public void dispatch(TestNoticePacket packet, PiServerPacketContext context) {
        }
    }

    private static final class DuplicatePacketIdBinding implements PiPacketBinding<OtherPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "test_notice");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<OtherPacket> packetType() {
            return OtherPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<OtherPacket> codec() {
            return noopCodec(new OtherPacket());
        }

        @Override
        public void dispatch(OtherPacket packet, PiServerPacketContext context) {
        }
    }

    private static <T> PiPacketCodec<T> noopCodec(T fallback) {
        return new PiPacketCodec<>() {
            @Override
            public void write(FriendlyByteBuf buffer, T value) {
            }

            @Override
            public T read(FriendlyByteBuf buffer, PiDecodeContext context) {
                return fallback;
            }
        };
    }

    private abstract static class AbstractPacket {
    }

    private static final class DirectionMismatchServerPacket extends org.pickaid.piserializekit.api.packet.PiServerPacket {
        @Override
        protected void handle(PiServerPacketContext context) {
        }
    }

    private static final class DirectionMismatchClientPacket extends org.pickaid.piserializekit.api.packet.PiClientPacket {
        @Override
        protected void handle(org.pickaid.piserializekit.api.packet.PiClientPacketContext context) {
        }
    }

    private static final class DirectionMismatchBidirectionalPacket extends org.pickaid.piserializekit.api.packet.PiBidirectionalPacket {
        @Override
        protected void handle(org.pickaid.piserializekit.api.packet.PiPacketContext context) {
        }
    }

    private static final class InvalidPacketVersionBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "invalid_packet_version");
        }

        @Override
        public int version() {
            return 0;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class WrongDirectionServerPacketBinding implements PiPacketBinding<DirectionMismatchServerPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "wrong_direction_server_packet");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<DirectionMismatchServerPacket> packetType() {
            return DirectionMismatchServerPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<DirectionMismatchServerPacket> codec() {
            return new DirectionMismatchServerPacketCodec();
        }

        @Override
        public void dispatch(DirectionMismatchServerPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class WrongDirectionClientPacketBinding implements PiPacketBinding<DirectionMismatchClientPacket, org.pickaid.piserializekit.api.packet.PiClientPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "wrong_direction_client_packet");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<DirectionMismatchClientPacket> packetType() {
            return DirectionMismatchClientPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<DirectionMismatchClientPacket> codec() {
            return new DirectionMismatchClientPacketCodec();
        }

        @Override
        public void dispatch(DirectionMismatchClientPacket packet, org.pickaid.piserializekit.api.packet.PiClientPacketContext context) {
        }
    }

    private static final class WrongDirectionBidirectionalPacketBinding implements PiPacketBinding<DirectionMismatchBidirectionalPacket, org.pickaid.piserializekit.api.packet.PiPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "wrong_direction_bidirectional_packet");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<DirectionMismatchBidirectionalPacket> packetType() {
            return DirectionMismatchBidirectionalPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<DirectionMismatchBidirectionalPacket> codec() {
            return new DirectionMismatchBidirectionalPacketCodec();
        }

        @Override
        public void dispatch(DirectionMismatchBidirectionalPacket packet, org.pickaid.piserializekit.api.packet.PiPacketContext context) {
        }
    }

    private static final class InvalidPacketFieldsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "invalid_packet_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of(new PiFieldKey(0, "Count Value"));
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class InvalidPacketMigrationsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "invalid_packet_migrations");
        }

        @Override
        public int version() {
            return 3;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(
                    PiSchemaMigration.step(1, 2, (payload, kind, context) -> payload),
                    PiSchemaMigration.step(1, 3, (payload, kind, context) -> payload)
            );
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class ReservedPacketFieldsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "reserved_packet_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of(new PiFieldKey(0, "__pi_value"));
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class SparsePacketFieldsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "sparse_packet_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of(new PiFieldKey(0, "first"), new PiFieldKey(2, "third"));
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketTypeBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_type");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return null;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketIdBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return null;
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketDirectionBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_direction");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return null;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketCodecBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_codec");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return null;
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketFieldsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return null;
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketMigrationsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_migrations");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return null;
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketFieldEntryBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_field_entry");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return java.util.Arrays.asList((PiFieldKey) null);
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NullPacketMigrationEntryBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "null_packet_migration_entry");
        }

        @Override
        public int version() {
            return 2;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return java.util.Arrays.asList((PiSchemaMigration) null);
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class AbstractPacketBinding implements PiPacketBinding<AbstractPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "abstract_packet_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<AbstractPacket> packetType() {
            return AbstractPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public PiPacketCodec<AbstractPacket> codec() {
            return new PiPacketCodec<>() {
                @Override
                public void write(FriendlyByteBuf buffer, AbstractPacket value) {
                }

                @Override
                public AbstractPacket read(FriendlyByteBuf buffer, PiDecodeContext context) {
                    return null;
                }
            };
        }

        @Override
        public void dispatch(AbstractPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class IncompletePacketMigrationsBinding implements PiPacketBinding<InvalidPacket, PiServerPacketContext> {
        @Override
        public ResourceLocation packetId() {
            return ResourceLocation.fromNamespaceAndPath("test", "incomplete_packet_migrations");
        }

        @Override
        public int version() {
            return 4;
        }

        @Override
        public PiPacketDirection direction() {
            return PiPacketDirection.SERVERBOUND;
        }

        @Override
        public Class<InvalidPacket> packetType() {
            return InvalidPacket.class;
        }

        @Override
        public List<PiFieldKey> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(
                    PiSchemaMigration.step(1, 2, (payload, kind, context) -> payload),
                    PiSchemaMigration.step(3, 4, (payload, kind, context) -> payload)
            );
        }

        @Override
        public PiPacketCodec<InvalidPacket> codec() {
            return new NoOpPacketCodec();
        }

        @Override
        public void dispatch(InvalidPacket packet, PiServerPacketContext context) {
        }
    }

    private static final class NoOpPacketCodec implements PiPacketCodec<InvalidPacket> {
        @Override
        public void write(FriendlyByteBuf buffer, InvalidPacket value) {
        }

        @Override
        public InvalidPacket read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return new InvalidPacket();
        }
    }

    private static final class DirectionMismatchServerPacketCodec implements PiPacketCodec<DirectionMismatchServerPacket> {
        @Override
        public void write(FriendlyByteBuf buffer, DirectionMismatchServerPacket packet) {
        }

        @Override
        public DirectionMismatchServerPacket read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return new DirectionMismatchServerPacket();
        }
    }

    private static final class DirectionMismatchClientPacketCodec implements PiPacketCodec<DirectionMismatchClientPacket> {
        @Override
        public void write(FriendlyByteBuf buffer, DirectionMismatchClientPacket packet) {
        }

        @Override
        public DirectionMismatchClientPacket read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return new DirectionMismatchClientPacket();
        }
    }

    private static final class DirectionMismatchBidirectionalPacketCodec implements PiPacketCodec<DirectionMismatchBidirectionalPacket> {
        @Override
        public void write(FriendlyByteBuf buffer, DirectionMismatchBidirectionalPacket packet) {
        }

        @Override
        public DirectionMismatchBidirectionalPacket read(FriendlyByteBuf buffer, PiDecodeContext context) {
            return new DirectionMismatchBidirectionalPacket();
        }
    }
}
