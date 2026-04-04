package org.pickaid.piserializekit.api.schema;

import java.util.Optional;

public interface PiSchemaRegistry {
    <T> void register(Class<T> type, PiStateBinding<T> binding);

    <T> Optional<PiStateBinding<T>> find(Class<T> type);

    <T> PiStateBinding<T> require(Class<T> type);
}
