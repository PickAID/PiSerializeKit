package org.pickaid.piserializekit.api.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiFieldKey;

class PiPacketApiSurfaceTest {
    @Test
    void packetBaseTypesExposeDirection() {
        final class DemoServerPacket extends PiServerPacket {
            @Override
            protected void handle(PiServerPacketContext context) {
            }
        }
        final class DemoClientPacket extends PiClientPacket {
            @Override
            protected void handle(PiClientPacketContext context) {
            }
        }
        final class DemoBiPacket extends PiBidirectionalPacket {
            @Override
            protected void handle(PiPacketContext context) {
            }
        }

        assertEquals(PiPacketDirection.SERVERBOUND, new DemoServerPacket().direction());
        assertEquals(PiPacketDirection.CLIENTBOUND, new DemoClientPacket().direction());
        assertEquals(PiPacketDirection.BIDIRECTIONAL, new DemoBiPacket().direction());
    }

    @Test
    void packetBindingUsesFieldKeysAndTypedDispatchContext() {
        final class DemoServerPacket extends PiServerPacket {
            @Override
            protected void handle(PiServerPacketContext context) {
            }
        }

        PiPacketBinding<DemoServerPacket, PiServerPacketContext> binding =
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

                    @Override
                    public void dispatch(DemoServerPacket packet, PiServerPacketContext context) {
                    }
                };

        assertEquals("value", binding.fields().get(0).id());
    }

    @Test
    void packetRegistryRoundTripUsesWildcardContextLookup() {
        final class DemoServerPacket extends PiServerPacket {
            @Override
            protected void handle(PiServerPacketContext context) {
            }
        }

        PiPacketBinding<DemoServerPacket, PiServerPacketContext> binding =
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

                    @Override
                    public void dispatch(DemoServerPacket packet, PiServerPacketContext context) {
                    }
                };

        PiPacketRegistry registry = new PiPacketRegistry() {
            private final Map<Class<?>, PiPacketBinding<?, ?>> bindings = new HashMap<>();
            private final Map<ResourceLocation, PiPacketBinding<?, ?>> bindingsById = new HashMap<>();

            @Override
            public <T> void register(Class<T> type, PiPacketBinding<T, ?> binding) {
                bindings.put(type, binding);
                bindingsById.put(binding.packetId(), binding);
            }

            @Override
            public <T> Optional<PiPacketBinding<T, ?>> find(Class<T> type) {
                return Optional.ofNullable(cast(type, bindings.get(type)));
            }

            @Override
            public <T> PiPacketBinding<T, ?> require(Class<T> type) {
                return find(type).orElseThrow();
            }

            @Override
            public Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId) {
                return Optional.ofNullable(bindingsById.get(packetId));
            }

            @Override
            public PiPacketBinding<?, ?> require(ResourceLocation packetId) {
                return find(packetId).orElseThrow();
            }

            @SuppressWarnings("unchecked")
            private static <T> PiPacketBinding<T, ?> cast(Class<T> type, PiPacketBinding<?, ?> binding) {
                return (PiPacketBinding<T, ?>) binding;
            }
        };

        registry.register(DemoServerPacket.class, binding);
        Optional<PiPacketBinding<DemoServerPacket, ?>> found = registry.find(DemoServerPacket.class);

        assertTrue(found.isPresent());
        assertSame(binding, found.get());
        assertSame(binding, registry.require(DemoServerPacket.class));
        assertSame(binding, registry.require(ResourceLocation.fromNamespaceAndPath("test", "demo_registry")));
        assertThrows(NoSuchElementException.class, () -> registry.require(String.class));
        assertThrows(NoSuchElementException.class, () -> registry.require(ResourceLocation.fromNamespaceAndPath("test", "missing")));
    }
}
