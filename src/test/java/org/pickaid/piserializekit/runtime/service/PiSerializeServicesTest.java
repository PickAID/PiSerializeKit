package org.pickaid.piserializekit.runtime.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializeServices;

class PiSerializeServicesTest {
    @Test
    void scopedOverrideUsesScopedServiceAndRestoresBaseService() {
        PiSerializeRuntime base = runtime();
        PiSerializeRuntime scoped = runtime();
        PiSerializeServices.install(base);

        assertSame(base, PiSerializeServices.require());

        PiSerializeService resolved = PiSerializeServices.withScope(scoped, PiSerializeServices::require);

        assertSame(scoped, resolved);
        assertSame(base, PiSerializeServices.require());
    }

    @Test
    void nestedScopedOverridesRestoreParentScope() {
        PiSerializeRuntime base = runtime();
        PiSerializeRuntime outer = runtime();
        PiSerializeRuntime inner = runtime();
        PiSerializeServices.install(base);

        PiSerializeServices.withScope(outer, () -> {
            assertSame(outer, PiSerializeServices.require());

            PiSerializeServices.withScope(inner, () -> {
                assertSame(inner, PiSerializeServices.require());
            });

            assertSame(outer, PiSerializeServices.require());
        });

        assertSame(base, PiSerializeServices.require());
    }

    @Test
    void scopedOverrideRestoresBaseServiceAfterFailure() {
        PiSerializeRuntime base = runtime();
        PiSerializeRuntime scoped = runtime();
        PiSerializeServices.install(base);

        assertThrows(IllegalStateException.class, () -> PiSerializeServices.withScope(scoped, () -> {
            throw new IllegalStateException("boom");
        }));

        assertSame(base, PiSerializeServices.require());
    }

    private static PiSerializeRuntime runtime() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        return runtime;
    }
}
