package org.pickaid.piserializekit.api.schema;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry view over generated schema bindings.
 */
public interface PiSchemaRegistry {
    /**
     * Registers one generated binding by state type and schema id.
     */
    <T> void register(Class<T> type, PiStateBinding<T> binding);

    /**
     * Finds a binding by authored state type.
     */
    <T> Optional<PiStateBinding<T>> find(Class<T> type);

    /**
     * Requires a binding by authored state type.
     */
    <T> PiStateBinding<T> require(Class<T> type);

    /**
     * Finds a binding by stable schema id.
     */
    Optional<PiStateBinding<?>> find(ResourceLocation schemaId);

    /**
     * Requires a binding by stable schema id.
     */
    PiStateBinding<?> require(ResourceLocation schemaId);

    /**
     * Returns known schema ids in stable author-facing order.
     */
    default List<ResourceLocation> schemaIds() {
        return List.of();
    }

    /**
     * Returns known state types in stable author-facing order.
     */
    default List<Class<?>> stateTypes() {
        return List.of();
    }
}
