package org.pickaid.piserializekit.api.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.packet.buffer.PiPacketBuffer;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.runtime.packet.fixture.TestNoticePacket;

class PiPacketApiSurfaceTest {
    @Test
    void packetBaseTypesExposeDirection() {
        final class DemoServerPacket extends PiServerPacket {}
        final class DemoClientPacket extends PiClientPacket {}
        final class DemoBiPacket extends PiBidirectionalPacket {}

        assertEquals(PiPacketDirection.SERVERBOUND, new DemoServerPacket().direction());
        assertEquals(PiPacketDirection.CLIENTBOUND, new DemoClientPacket().direction());
        assertEquals(PiPacketDirection.BIDIRECTIONAL, new DemoBiPacket().direction());
    }

    @Test
    void packetBaseTypesExposeSelfWriteAndMetadataMethods() throws Exception {
        TestNoticePacket packet = new TestNoticePacket("", List.of());

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "test_notice"), packet.packetId());
        assertEquals(1, packet.version());
        assertSame(
                FriendlyByteBuf.class,
                PiServerPacket.class.getMethod("write", FriendlyByteBuf.class).getParameterTypes()[0]
        );
        assertSame(
                PiPacketBuffer.class,
                PiServerPacket.class.getMethod("write", PiPacketBuffer.class).getParameterTypes()[0]
        );
    }

    @Test
    void packetTypesNoLongerExposeDispatchHandleContracts() {
        assertTrue(java.util.Arrays.stream(PiServerPacket.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("handle")));
        assertTrue(java.util.Arrays.stream(PiClientPacket.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("handle")));
        assertTrue(java.util.Arrays.stream(PiBidirectionalPacket.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("handle")));
        assertTrue(java.util.Arrays.stream(PiPacketBinding.class.getMethods()).noneMatch(method -> method.getName().equals("dispatch")));
    }

    @Test
    void packetBindingUsesFieldKeysWithoutDispatchContext() {
        final class DemoServerPacket extends PiServerPacket {}

        PiPacketBinding<DemoServerPacket> binding =
                new PiPacketBinding<>() {
                    @Override
                    public ResourceLocation packetId() {
                        return ResourceLocation.fromNamespaceAndPath("test", "demo_server");
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
                    public Class<DemoServerPacket> packetType() {
                        return DemoServerPacket.class;
                    }

                    @Override
                    public List<PiFieldKey> fields() {
                        return List.of(new PiFieldKey(0, "value"));
                    }

                    @Override
                    public PiPacketCodec<DemoServerPacket> codec() {
                        return null;
                    }
                };

        assertEquals("value", binding.fields().get(0).id());
    }

    @Test
    void packetRegistryRoundTripUsesTypeLookup() {
        final class DemoServerPacket extends PiServerPacket {}

        PiPacketBinding<DemoServerPacket> binding =
                new PiPacketBinding<>() {
                    @Override
                    public ResourceLocation packetId() {
                        return ResourceLocation.fromNamespaceAndPath("test", "demo_registry");
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
                    public Class<DemoServerPacket> packetType() {
                        return DemoServerPacket.class;
                    }

                    @Override
                    public List<PiFieldKey> fields() {
                        return List.of();
                    }

                    @Override
                    public PiPacketCodec<DemoServerPacket> codec() {
                        return null;
                    }
                };

        PiPacketRegistry registry = new PiPacketRegistry() {
            private final Map<Class<?>, PiPacketBinding<?>> bindings = new HashMap<>();
            private final Map<ResourceLocation, PiPacketBinding<?>> bindingsById = new HashMap<>();

            @Override
            public <T> void register(Class<T> type, PiPacketBinding<T> binding) {
                bindings.put(type, binding);
                bindingsById.put(binding.packetId(), binding);
            }

            @Override
            public <T> Optional<PiPacketBinding<T>> find(Class<T> type) {
                return Optional.ofNullable(cast(type, bindings.get(type)));
            }

            @Override
            public <T> PiPacketBinding<T> require(Class<T> type) {
                return find(type).orElseThrow();
            }

            @Override
            public Optional<PiPacketBinding<?>> find(ResourceLocation packetId) {
                return Optional.ofNullable(bindingsById.get(packetId));
            }

            @Override
            public PiPacketBinding<?> require(ResourceLocation packetId) {
                return find(packetId).orElseThrow();
            }

            @SuppressWarnings("unchecked")
            private static <T> PiPacketBinding<T> cast(Class<T> type, PiPacketBinding<?> binding) {
                return (PiPacketBinding<T>) binding;
            }
        };

        registry.register(DemoServerPacket.class, binding);
        Optional<PiPacketBinding<DemoServerPacket>> found = registry.find(DemoServerPacket.class);

        assertTrue(found.isPresent());
        assertSame(binding, found.get());
        assertSame(binding, registry.require(DemoServerPacket.class));
        assertSame(binding, registry.require(ResourceLocation.fromNamespaceAndPath("test", "demo_registry")));
        assertThrows(NoSuchElementException.class, () -> registry.require(String.class));
        assertThrows(NoSuchElementException.class, () -> registry.require(ResourceLocation.fromNamespaceAndPath("test", "missing")));
    }

    @Test
    void packetCodecStrictReadThrowsStructuredDecodeException() {
        PiPacketCodec<String> codec = new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, String value) {
            }

            @Override
            public String read(PiPacketBuffer buffer, PiDecodeContext context) {
                context.issue(PiDecodeIssueCode.INVALID_VALUE, "value", "boom", true);
                return "";
            }
        };

        PiPacketCodecDecodeException exception = assertThrows(
                PiPacketCodecDecodeException.class,
                () -> codec.read(new FriendlyByteBuf(Unpooled.buffer()))
        );

        assertEquals("value -> boom", exception.result().summary());
        assertEquals("Failed to decode Pi packet payload [fatal]: value -> boom", exception.getMessage());
    }

    @Test
    void packetCodecImplementationsCanTargetStablePacketBufferSurfaceDirectly() {
        PiPacketCodec<String> codec = new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, String value) {
                buffer.writeUtf(value);
            }

            @Override
            public String read(PiPacketBuffer buffer, PiDecodeContext context) {
                return buffer.readUtf();
            }
        };

        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.write(raw, "blink");
            assertEquals("blink", codec.read(raw));
        } finally {
            raw.release();
        }
    }

    @Test
    void packetCodecStrictReadWrapsThrownRuntimeExceptionIntoStructuredDecodeException() {
        PiPacketCodec<String> codec = new PiPacketCodec<>() {
            @Override
            public void write(PiPacketBuffer buffer, String value) {
            }

            @Override
            public String read(PiPacketBuffer buffer, PiDecodeContext context) {
                throw new IllegalStateException("   ");
            }
        };

        PiPacketCodecDecodeException exception = assertThrows(
                PiPacketCodecDecodeException.class,
                () -> codec.read(new FriendlyByteBuf(Unpooled.buffer()))
        );

        assertTrue(exception.result().hasFatal());
        assertEquals("$", exception.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, exception.result().issues().get(0).code());
        assertEquals("packet decode failed: IllegalStateException", exception.result().issues().get(0).message());
    }
}
