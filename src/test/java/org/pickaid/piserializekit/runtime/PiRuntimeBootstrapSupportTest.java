package org.pickaid.piserializekit.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.runtime.PiRuntimeBootstrapException;

class PiRuntimeBootstrapSupportTest {
    @Test
    void registerProvidersWrapsProviderFailureWithProviderClass() {
        PiRuntimeBootstrapException exception = assertThrows(
                PiRuntimeBootstrapException.class,
                () -> PiRuntimeBootstrapSupport.registerProviders(
                        "Pi packet",
                        List.of(new ThrowingProvider()),
                        provider -> ((ThrowingProvider) provider).register()
                )
        );

        assertEquals(
                "Failed to register Pi packet provider " + ThrowingProvider.class.getName() + ": packet provider boom",
                exception.getMessage()
        );
        assertEquals("Pi packet", exception.category());
        assertEquals(ThrowingProvider.class.getName(), exception.providerClassName());
    }

    @Test
    void registerProvidersWrapsServiceDiscoveryFailure() {
        Iterable<Object> failingIterable = () -> {
            throw new ServiceConfigurationError("broken services");
        };

        PiRuntimeBootstrapException exception = assertThrows(
                PiRuntimeBootstrapException.class,
                () -> PiRuntimeBootstrapSupport.registerProviders("Pi schema", failingIterable, provider -> {
                })
        );

        assertEquals(
                "Failed to discover Pi schema providers from ServiceLoader: broken services",
                exception.getMessage()
        );
        assertEquals("Pi schema", exception.category());
        assertNull(exception.providerClassName());
    }

    @Test
    void createFromFirstProviderWrapsFactoryFailure() {
        PiRuntimeBootstrapException exception = assertThrows(
                PiRuntimeBootstrapException.class,
                () -> PiRuntimeBootstrapSupport.createFromFirstProvider(
                        "default Pi serializer service",
                        List.of(new ThrowingFactoryProvider()),
                        provider -> ((ThrowingFactoryProvider) provider).create()
                )
        );

        assertEquals(
                "Failed to create default Pi serializer service from provider "
                        + ThrowingFactoryProvider.class.getName()
                        + ": serializer service boom",
                exception.getMessage()
        );
        assertEquals("default Pi serializer service", exception.category());
        assertEquals(ThrowingFactoryProvider.class.getName(), exception.providerClassName());
    }

    @Test
    void createFromFirstProviderWrapsNullFactoryResult() {
        PiRuntimeBootstrapException exception = assertThrows(
                PiRuntimeBootstrapException.class,
                () -> PiRuntimeBootstrapSupport.createFromFirstProvider(
                        "default Pi serializer service",
                        List.of(new NullFactoryProvider()),
                        provider -> ((NullFactoryProvider) provider).create()
                )
        );

        assertEquals(
                "Failed to create default Pi serializer service from provider "
                        + NullFactoryProvider.class.getName()
                        + ": provider factory returned null",
                exception.getMessage()
        );
        assertEquals("default Pi serializer service", exception.category());
        assertEquals(NullFactoryProvider.class.getName(), exception.providerClassName());
    }

    @Test
    void createFromFirstProviderReturnsNullWhenNoProvidersExist() {
        assertNull(PiRuntimeBootstrapSupport.createFromFirstProvider("default Pi serializer service", List.of(), provider -> provider));
    }

    private static final class ThrowingProvider {
        void register() {
            throw new IllegalStateException("packet provider boom");
        }
    }

    private static final class ThrowingFactoryProvider {
        Object create() {
            throw new IllegalStateException("serializer service boom");
        }
    }

    private static final class NullFactoryProvider {
        Object create() {
            return null;
        }
    }
}
