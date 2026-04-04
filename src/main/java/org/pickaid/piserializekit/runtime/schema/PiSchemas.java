package org.pickaid.piserializekit.runtime.schema;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.pickaid.piserializekit.api.schema.PiSchemaProvider;
import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;
import org.pickaid.piserializekit.api.schema.PiStateBinding;

public final class PiSchemas {
    private static final PiSchemaRegistry REGISTRY = new ServiceLoaderRegistry();

    private PiSchemas() {
    }

    public static <T> PiStateBinding<T> require(Class<T> type) {
        return REGISTRY.require(type);
    }

    private static final class ServiceLoaderRegistry implements PiSchemaRegistry {
        private final Map<Class<?>, PiStateBinding<?>> bindings = new ConcurrentHashMap<>();
        private volatile boolean loaded;

        @Override
        public <T> void register(Class<T> type, PiStateBinding<T> binding) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(binding, "binding");
            if (!type.equals(binding.stateType())) {
                throw new IllegalArgumentException("Binding state type mismatch: " + type.getName() + " != " + binding.stateType().getName());
            }
            bindings.put(type, binding);
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
            return find(type).orElseThrow(() -> new IllegalStateException("Missing Pi schema binding for " + type.getName()));
        }

        private synchronized void ensureLoaded() {
            if (loaded) {
                return;
            }
            ServiceLoader.load(PiSchemaProvider.class).forEach(provider -> provider.register(this));
            loaded = true;
        }

        @SuppressWarnings("unchecked")
        private static <T> PiStateBinding<T> castBinding(PiStateBinding<?> binding) {
            return (PiStateBinding<T>) binding;
        }
    }
}
