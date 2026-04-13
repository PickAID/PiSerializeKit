package org.pickaid.piserializekit.runtime.packet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketProvider;
import org.pickaid.piserializekit.api.packet.PiPacketRegistry;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.runtime.PiRuntimeBootstrapSupport;
import org.pickaid.piserializekit.runtime.PiRuntimeBindingValidation;

/**
 * Service-loader packet binding registry for runtime lookup by class or packet id.
 */
public final class PiPackets {
    private static final PiPacketRegistry REGISTRY = new ServiceLoaderRegistry();

    private PiPackets() {
    }

    /**
     * Finds a generated packet binding by authored packet type.
     */
    public static <T> Optional<PiPacketBinding<T, ?>> find(Class<T> type) {
        return REGISTRY.find(type);
    }

    /**
     * Requires a generated packet binding by authored packet type.
     */
    public static <T> PiPacketBinding<T, ?> require(Class<T> type) {
        return REGISTRY.require(type);
    }

    /**
     * Finds a generated packet binding by stable packet id.
     */
    public static Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId) {
        return REGISTRY.find(packetId);
    }

    /**
     * Requires a generated packet binding by stable packet id.
     */
    public static PiPacketBinding<?, ?> require(ResourceLocation packetId) {
        return REGISTRY.require(packetId);
    }

    /**
     * Returns all known packet ids in stable order for diagnostics and tooling.
     */
    public static List<ResourceLocation> packetIds() {
        return REGISTRY.packetIds();
    }

    /**
     * Returns all known packet types in stable order for diagnostics and tooling.
     */
    public static List<Class<?>> packetTypes() {
        return REGISTRY.packetTypes();
    }

    private static final class ServiceLoaderRegistry implements PiPacketRegistry {
        private final Map<Class<?>, PiPacketBinding<?, ?>> bindingsByType = new ConcurrentHashMap<>();
        private final Map<ResourceLocation, PiPacketBinding<?, ?>> bindingsById = new ConcurrentHashMap<>();
        private volatile boolean loaded;

        @Override
        public <T> void register(Class<T> type, PiPacketBinding<T, ?> binding) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(binding, "binding");
            PiRuntimeBindingValidation.validatePacketBinding(binding);
            ResourceLocation packetId = Objects.requireNonNull(binding.packetId(), "binding.packetId()");
            Class<?> packetType = Objects.requireNonNull(binding.packetType(), "binding.packetType()");
            if (!type.equals(binding.packetType())) {
                throw new IllegalArgumentException(
                        "Binding packet type mismatch: " + type.getName() + " != " + binding.packetType().getName()
                );
            }
            PiPacketBinding<?, ?> previousByType = bindingsByType.putIfAbsent(type, binding);
            if (previousByType != null && previousByType != binding) {
                throw new PiRuntimeConflictException(
                        "packet-type",
                        type.getName(),
                        "Duplicate Pi packet binding for type " + type.getName()
                                + "; existing packet id " + previousByType.packetId()
                                + ", conflicting packet id " + packetId
                );
            }
            PiPacketBinding<?, ?> previousById = bindingsById.putIfAbsent(packetId, binding);
            if (previousById != null && previousById != binding) {
                throw new PiRuntimeConflictException(
                        "packet-id",
                        packetId.toString(),
                        "Duplicate Pi packet binding for id " + packetId
                                + "; existing packet type " + previousById.packetType().getName()
                                + ", conflicting packet type " + packetType.getName()
                );
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
            return find(type).orElseThrow(() -> new PiRuntimeLookupException(
                    "packet-type",
                    type.getName(),
                    "Missing Pi packet binding for " + type.getName()
                            + "; known packet ids: " + describeKnownPacketIds()
                            + emptyRegistryHint()
            ));
        }

        @Override
        public Optional<PiPacketBinding<?, ?>> find(ResourceLocation packetId) {
            ensureLoaded();
            return Optional.ofNullable(bindingsById.get(Objects.requireNonNull(packetId, "packetId")));
        }

        @Override
        public PiPacketBinding<?, ?> require(ResourceLocation packetId) {
            return find(packetId).orElseThrow(() -> new PiRuntimeLookupException(
                    "packet-id",
                    packetId.toString(),
                    "Missing Pi packet binding for " + packetId
                            + "; known packet ids: " + describeKnownPacketIds()
                            + emptyRegistryHint()
            ));
        }

        @Override
        public List<ResourceLocation> packetIds() {
            ensureLoaded();
            ArrayList<ResourceLocation> ids = new ArrayList<>(bindingsById.keySet());
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            return List.copyOf(ids);
        }

        @Override
        public List<Class<?>> packetTypes() {
            ensureLoaded();
            ArrayList<Class<?>> types = new ArrayList<>(bindingsByType.keySet());
            types.sort(Comparator.comparing(Class::getName));
            return List.copyOf(types);
        }

        private synchronized void ensureLoaded() {
            if (loaded) {
                return;
            }
            PiRuntimeBootstrapSupport.registerProviders(
                    "Pi packet",
                    ServiceLoader.load(PiPacketProvider.class),
                    provider -> provider.register(this)
            );
            loaded = true;
        }

        @SuppressWarnings("unchecked")
        private static <T> PiPacketBinding<T, ?> castBinding(PiPacketBinding<?, ?> binding) {
            return (PiPacketBinding<T, ?>) binding;
        }

        private String describeKnownPacketIds() {
            if (bindingsById.isEmpty()) {
                return "<none>";
            }
            java.util.ArrayList<String> ids = new java.util.ArrayList<>(bindingsById.size());
            for (ResourceLocation id : bindingsById.keySet()) {
                ids.add(id.toString());
            }
            ids.sort(String::compareTo);
            int limit = Math.min(6, ids.size());
            String joined = String.join(", ", ids.subList(0, limit));
            if (ids.size() > limit) {
                return joined + ", +" + (ids.size() - limit) + " more";
            }
            return joined;
        }

        private String emptyRegistryHint() {
            if (!bindingsById.isEmpty()) {
                return "";
            }
            return "; no PiPacketProvider entries were loaded. Ensure annotation processing generated packet providers "
                    + "and META-INF/services resources are on the runtime classpath.";
        }
    }
}
