package org.pickaid.piserializekit.runtime.schema.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.api.schema.PiSchemaProvider;
import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.runtime.PiRuntimeBootstrapSupport;
import org.pickaid.piserializekit.runtime.PiRuntimeBindingValidation;

public final class PiSchemas {
    private static final PiSchemaRegistry REGISTRY = new ServiceLoaderRegistry();

    private PiSchemas() {
    }

    /**
     * Finds a generated schema binding by authored state type.
     */
    public static <T> Optional<PiStateBinding<T>> find(Class<T> type) {
        return REGISTRY.find(type);
    }

    /**
     * Requires a generated schema binding by authored state type.
     */
    public static <T> PiStateBinding<T> require(Class<T> type) {
        return REGISTRY.require(type);
    }

    /**
     * Finds a generated schema binding by stable schema id.
     */
    public static Optional<PiStateBinding<?>> find(ResourceLocation schemaId) {
        return REGISTRY.find(schemaId);
    }

    /**
     * Requires a generated schema binding by stable schema id.
     */
    public static PiStateBinding<?> require(ResourceLocation schemaId) {
        return REGISTRY.require(schemaId);
    }

    /**
     * Returns all known schema ids in stable order for diagnostics and tooling.
     */
    public static List<ResourceLocation> schemaIds() {
        return REGISTRY.schemaIds();
    }

    /**
     * Returns all known state types in stable order for diagnostics and tooling.
     */
    public static List<Class<?>> stateTypes() {
        return REGISTRY.stateTypes();
    }

    private static final class ServiceLoaderRegistry implements PiSchemaRegistry {
        private final Map<Class<?>, PiStateBinding<?>> bindings = new ConcurrentHashMap<>();
        private final Map<ResourceLocation, PiStateBinding<?>> bindingsById = new ConcurrentHashMap<>();
        private volatile boolean loaded;

        @Override
        public <T> void register(Class<T> type, PiStateBinding<T> binding) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(binding, "binding");
            PiRuntimeBindingValidation.validateSchemaBinding(binding);
            ResourceLocation schemaId = Objects.requireNonNull(binding.schemaId(), "binding.schemaId()");
            Class<?> stateType = Objects.requireNonNull(binding.stateType(), "binding.stateType()");
            if (!type.equals(binding.stateType())) {
                throw new IllegalArgumentException("Binding state type mismatch: " + type.getName() + " != " + binding.stateType().getName());
            }
            PiStateBinding<?> previousByType = bindings.putIfAbsent(type, binding);
            if (previousByType != null && previousByType != binding) {
                throw new PiRuntimeConflictException(
                        "state-type",
                        type.getName(),
                        "Duplicate Pi schema binding for type " + type.getName()
                                + "; existing schema id " + previousByType.schemaId()
                                + ", conflicting schema id " + schemaId
                );
            }
            PiStateBinding<?> previousById = bindingsById.putIfAbsent(schemaId, binding);
            if (previousById != null && previousById != binding) {
                throw new PiRuntimeConflictException(
                        "schema-id",
                        schemaId.toString(),
                        "Duplicate Pi schema binding for id " + schemaId
                                + "; existing state type " + previousById.stateType().getName()
                                + ", conflicting state type " + stateType.getName()
                );
            }
        }

        @Override
        public <T> Optional<PiStateBinding<T>> find(Class<T> type) {
            ensureLoaded();
            PiStateBinding<?> binding = bindings.get(Objects.requireNonNull(type, "type"));
            if (binding == null) {
                return Optional.empty();
            }
            return Optional.of(castBinding(binding));
        }

        @Override
        public <T> PiStateBinding<T> require(Class<T> type) {
            return find(type).orElseThrow(() -> new PiRuntimeLookupException(
                    "state-type",
                    type.getName(),
                    "Missing Pi schema binding for " + type.getName()
                            + "; known schema ids: " + describeKnownSchemaIds()
                            + emptyRegistryHint()
            ));
        }

        @Override
        public Optional<PiStateBinding<?>> find(ResourceLocation schemaId) {
            ensureLoaded();
            return Optional.ofNullable(bindingsById.get(Objects.requireNonNull(schemaId, "schemaId")));
        }

        @Override
        public PiStateBinding<?> require(ResourceLocation schemaId) {
            return find(schemaId).orElseThrow(() -> new PiRuntimeLookupException(
                    "schema-id",
                    schemaId.toString(),
                    "Missing Pi schema binding for " + schemaId
                            + "; known schema ids: " + describeKnownSchemaIds()
                            + emptyRegistryHint()
            ));
        }

        @Override
        public List<ResourceLocation> schemaIds() {
            ensureLoaded();
            ArrayList<ResourceLocation> ids = new ArrayList<>(bindingsById.keySet());
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            return List.copyOf(ids);
        }

        @Override
        public List<Class<?>> stateTypes() {
            ensureLoaded();
            ArrayList<Class<?>> types = new ArrayList<>(bindings.keySet());
            types.sort(Comparator.comparing(Class::getName));
            return List.copyOf(types);
        }

        private synchronized void ensureLoaded() {
            if (loaded) {
                return;
            }
            PiRuntimeBootstrapSupport.registerProviders(
                    "Pi schema",
                    ServiceLoader.load(PiSchemaProvider.class),
                    provider -> provider.register(this)
            );
            loaded = true;
        }

        @SuppressWarnings("unchecked")
        private static <T> PiStateBinding<T> castBinding(PiStateBinding<?> binding) {
            return (PiStateBinding<T>) binding;
        }

        private String describeKnownSchemaIds() {
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
            return "; no PiSchemaProvider entries were loaded. Ensure annotation processing generated schema providers "
                    + "and META-INF/services resources are on the runtime classpath.";
        }
    }
}
