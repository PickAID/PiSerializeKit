package org.pickaid.piserializekit.api.service;

import java.util.Objects;
import java.util.Optional;

public final class PiSerializeServices {
    private static volatile PiSerializeService service;

    private PiSerializeServices() {
    }

    public static void install(PiSerializeService serializeService) {
        service = Objects.requireNonNull(serializeService, "serializeService");
    }

    public static Optional<PiSerializeService> find() {
        return Optional.ofNullable(service);
    }

    public static PiSerializeService require() {
        return find().orElseThrow(() -> new IllegalStateException("PiSerializeKit service has not been installed"));
    }
}
