package org.pickaid.piserializekit.runtime;

import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pickaid.piserializekit.api.runtime.PiRuntimeBootstrapException;

/**
 * Shared runtime bootstrap helpers for provider discovery and provider-backed service creation.
 */
public final class PiRuntimeBootstrapSupport {
    private PiRuntimeBootstrapSupport() {
    }

    public static <P> void registerProviders(String providerKind, Iterable<? extends P> providers, Consumer<? super P> registrar) {
        Objects.requireNonNull(providerKind, "providerKind");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(registrar, "registrar");
        try {
            for (P provider : providers) {
                try {
                    registrar.accept(provider);
                } catch (Throwable throwable) {
                    throw new PiRuntimeBootstrapException(
                            providerKind,
                            provider.getClass().getName(),
                            "Failed to register " + providerKind + " provider " + provider.getClass().getName() + ": " + describeThrowable(throwable),
                            throwable
                    );
                }
            }
        } catch (ServiceConfigurationError error) {
            throw new PiRuntimeBootstrapException(
                    providerKind,
                    null,
                    "Failed to discover " + providerKind + " providers from ServiceLoader: " + describeThrowable(error),
                    error
            );
        }
    }

    public static <P, T> T createFromFirstProvider(
            String subject,
            Iterable<? extends P> providers,
            Function<? super P, ? extends T> factory
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(factory, "factory");
        try {
            for (P provider : providers) {
                try {
                    T created = factory.apply(provider);
                    if (created == null) {
                        throw new PiRuntimeBootstrapException(subject, provider.getClass().getName(), "provider factory returned null");
                    }
                    return created;
                } catch (Throwable throwable) {
                    throw new PiRuntimeBootstrapException(
                            subject,
                            provider.getClass().getName(),
                            "Failed to create " + subject + " from provider " + provider.getClass().getName() + ": " + describeThrowable(throwable),
                            throwable
                    );
                }
            }
            return null;
        } catch (ServiceConfigurationError error) {
            throw new PiRuntimeBootstrapException(
                    subject,
                    null,
                    "Failed to discover " + subject + " providers from ServiceLoader: " + describeThrowable(error),
                    error
            );
        }
    }

    public static String describeThrowable(Throwable throwable) {
        String detail = throwable.getMessage();
        if (detail == null || detail.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return detail;
    }
}
