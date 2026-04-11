package org.pickaid.piserializekit.runtime.packet;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketProvider;
import org.pickaid.piserializekit.api.packet.PiPacketRegistry;

/**
 * Service-loader packet binding registry for runtime lookup by class or packet id.
 */
public final class PiPackets {
    private static final PiPacketRegistry REGISTRY = new ServiceLoaderRegistry();

    private PiPackets() {
    }

    public static <T> Optional<PiPacketBinding<T, ?>> find(Class<T> type) {
        return REGISTRY.find(type);
    }

    public static <T> PiPacketBinding<T, ?> require(Class<T> type) {
        return REGISTRY.require(type);
    }

    public static Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId) {
        return REGISTRY.find(packetId);
    }

    public static PiPacketBinding<?, ?> require(ResourceLocation packetId) {
        return REGISTRY.require(packetId);
    }

    private static final class ServiceLoaderRegistry implements PiPacketRegistry {
        private final Map<Class<?>, PiPacketBinding<?, ?>> bindingsByType = new ConcurrentHashMap<>();
        private final Map<ResourceLocation, PiPacketBinding<?, ?>> bindingsById = new ConcurrentHashMap<>();
        private volatile boolean loaded;

        @Override
        public <T> void register(Class<T> type, PiPacketBinding<T, ?> binding) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(binding, "binding");
            if (!type.equals(binding.packetType())) {
                throw new IllegalArgumentException(
                        "Binding packet type mismatch: " + type.getName() + " != " + binding.packetType().getName()
                );
            }
            PiPacketBinding<?, ?> previousByType = bindingsByType.putIfAbsent(type, binding);
            if (previousByType != null && previousByType != binding) {
                throw new IllegalStateException("Duplicate Pi packet binding for type " + type.getName());
            }
            ResourceLocation packetId = Objects.requireNonNull(binding.packetId(), "binding.packetId()");
            PiPacketBinding<?, ?> previousById = bindingsById.putIfAbsent(packetId, binding);
            if (previousById != null && previousById != binding) {
                throw new IllegalStateException("Duplicate Pi packet binding for id " + packetId);
            }
        }

        @Override
        public <T> Optional<PiPacketBinding<T, ?>> find(Class<T> type) {
            ensureLoaded();
            PiPacketBinding<?, ?> binding = bindingsByType.get(Objects.requireNonNull(type, "type"));
            if (binding == null) {
                return Optional.empty();
            }
            return Optional.of(castBinding(binding));
        }

        @Override
        public <T> PiPacketBinding<T, ?> require(Class<T> type) {
            return find(type).orElseThrow(() -> new IllegalStateException("Missing Pi packet binding for " + type.getName()));
        }

        @Override
        public Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId) {
            ensureLoaded();
            return Optional.ofNullable(bindingsById.get(Objects.requireNonNull(packetId, "packetId")));
        }

        @Override
        public PiPacketBinding<?, ?> require(ResourceLocation packetId) {
            return find(packetId).orElseThrow(() -> new IllegalStateException("Missing Pi packet binding for " + packetId));
        }

        private synchronized void ensureLoaded() {
            if (loaded) {
                return;
            }
            ServiceLoader.load(PiPacketProvider.class).forEach(provider -> provider.register(this));
            loaded = true;
        }

        @SuppressWarnings("unchecked")
        private static <T> PiPacketBinding<T, ?> castBinding(PiPacketBinding<?, ?> binding) {
            return (PiPacketBinding<T, ?>) binding;
        }
    }
}
