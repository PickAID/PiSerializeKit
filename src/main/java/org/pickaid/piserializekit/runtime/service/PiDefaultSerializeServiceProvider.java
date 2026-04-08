package org.pickaid.piserializekit.runtime.service;

import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializeServiceProvider;

/**
 * Supplies the built-in serializer runtime used by generated schema bindings.
 */
public final class PiDefaultSerializeServiceProvider implements PiSerializeServiceProvider {
    @Override
    public PiSerializeService create() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        return runtime;
    }
}
