package org.pickaid.piserializekit.api.service;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

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
        return find().orElseThrow(() -> new IllegalStateException("PiSerializeKit service has not been installed"));
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
            PiSerializeServiceProvider provider = ServiceLoader.load(PiSerializeServiceProvider.class)
                    .findFirst()
                    .orElse(null);
            if (provider == null) {
                return null;
            }
            PiSerializeService created = Objects.requireNonNull(provider.create(), "provider.create()");
            service = created;
            return created;
        }
    }
}
