package org.pickaid.piserializekit.api.service;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.runtime.PiRuntimeBootstrapSupport;

public final class PiSerializeServices {
    private static volatile PiSerializeService service;
    private static final ThreadLocal<ArrayDeque<PiSerializeService>> scopedServices = new ThreadLocal<>();

    private PiSerializeServices() {
    }

    /**
     * Installs the process-wide serializer service used when no scoped override is active.
     */
    public static void install(PiSerializeService serializeService) {
        service = Objects.requireNonNull(serializeService, "serializeService");
    }

    /**
     * Resolves the current serializer service, preferring a scoped override when present.
     */
    public static Optional<PiSerializeService> find() {
        PiSerializeService scoped = scoped();
        return Optional.ofNullable(scoped != null ? scoped : service != null ? service : loadDefault());
    }

    /**
     * Resolves the current serializer service or fails when neither a scoped nor global service exists.
     */
    public static PiSerializeService require() {
        return find().orElseThrow(() -> new PiRuntimeLookupException(
                "serializer-service",
                "default",
                "PiSerializeKit service has not been installed; no PiSerializeServiceProvider entries were loaded. "
                        + "Install a serializer service explicitly or ensure runtime provider resources are on the classpath."
        ));
    }

    /**
     * Resolves one serializer from the current service or fails with an author-facing diagnostic.
     */
    public static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type) {
        Objects.requireNonNull(type, "type");
        return require().require(type);
    }

    /**
     * Finds one serializer from the current service without throwing when it is absent.
     */
    public static <T> Optional<PiSerializer<T>> findSerializer(PiSerializerType<T> type) {
        Objects.requireNonNull(type, "type");
        return find().flatMap(service -> service.lookup(type));
    }

    /**
     * Finds one serializer from the current service by id and java type.
     */
    public static <T> Optional<PiSerializer<T>> findSerializer(ResourceLocation id, Class<T> javaType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(javaType, "javaType");
        return findSerializer(new PiSerializerType<>(id, javaType));
    }

    /**
     * Resolves one serializer from the current service by id and java type or fails with an author-facing diagnostic.
     */
    public static <T> PiSerializer<T> requireSerializer(ResourceLocation id, Class<T> javaType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(javaType, "javaType");
        return requireSerializer(new PiSerializerType<>(id, javaType));
    }

    /**
     * Returns known serializer ids from the current service in stable order.
     */
    public static List<ResourceLocation> serializerIds() {
        return require().serializerIds();
    }

    /**
     * Returns known serializer java types from the current service in stable order.
     */
    public static List<Class<?>> serializerJavaTypes() {
        return require().serializerJavaTypes();
    }

    /**
     * Runs the given action with a temporary serializer service override for the current thread.
     */
    public static void withScope(PiSerializeService serializeService, Runnable action) {
        Objects.requireNonNull(action, "action");
        withScope(serializeService, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs the given action with a temporary serializer service override for the current thread.
     */
    public static <T> T withScope(PiSerializeService serializeService, Supplier<T> action) {
        Objects.requireNonNull(serializeService, "serializeService");
        Objects.requireNonNull(action, "action");
        ArrayDeque<PiSerializeService> stack = scopedServices.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            scopedServices.set(stack);
        }
        stack.push(serializeService);
        try {
            return action.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                scopedServices.remove();
            }
        }
    }

    private static PiSerializeService scoped() {
        ArrayDeque<PiSerializeService> stack = scopedServices.get();
        return stack == null ? null : stack.peek();
    }

    private static PiSerializeService loadDefault() {
        PiSerializeService current = service;
        if (current != null) {
            return current;
        }
        synchronized (PiSerializeServices.class) {
            if (service != null) {
                return service;
            }
            PiSerializeService created = PiRuntimeBootstrapSupport.createFromFirstProvider(
                    "default Pi serializer service",
                    ServiceLoader.load(PiSerializeServiceProvider.class),
                    PiSerializeServiceProvider::create
            );
            if (created == null) {
                return null;
            }
            service = created;
            return created;
        }
    }
}
