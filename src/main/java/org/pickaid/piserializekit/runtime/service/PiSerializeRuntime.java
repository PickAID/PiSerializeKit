package org.pickaid.piserializekit.runtime.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializer;

public final class PiSerializeRuntime implements PiSerializeService {
    private final Map<ResourceLocation, Entry<?>> serializers = new ConcurrentHashMap<>();

    @Override
    public <T> void register(ResourceLocation id, PiSerializer<T> serializer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(serializer, "serializer");
        Entry<?> previous = serializers.putIfAbsent(id, new Entry<>(serializer));
        if (previous != null) {
            throw new IllegalStateException("Duplicate Pi serializer registration for " + id);
        }
    }

    @Override
    public <T> Optional<PiSerializer<T>> lookup(ResourceLocation id, Class<T> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Entry<?> entry = serializers.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(cast(entry.serializer()));
    }

    @SuppressWarnings("unchecked")
    private static <T> PiSerializer<T> cast(PiSerializer<?> serializer) {
        return (PiSerializer<T>) serializer;
    }

    private record Entry<T>(PiSerializer<T> serializer) {
        private Entry {
            Objects.requireNonNull(serializer, "serializer");
        }
    }
}
