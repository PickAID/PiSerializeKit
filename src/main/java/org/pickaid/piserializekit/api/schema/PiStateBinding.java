package org.pickaid.piserializekit.api.schema;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Runtime binding for one generated sync schema.
 *
 * @param <T> state type
 */
public interface PiStateBinding<T> extends PiSyncSchema<T> {
    /**
     * Returns the schema identifier used for payload headers and diagnostics.
     *
     * @return schema id
     */
    ResourceLocation schemaId();

    /**
     * Returns the generated schema version.
     *
     * @return schema version
     */
    int version();

    /**
     * Returns the bound state type.
     *
     * @return state class
     */
    Class<T> stateType();

    /**
     * Creates a fresh state instance.
     *
     * @return new state
     */
    T newState();

    /**
     * Returns the declared fields for the schema.
     *
     * @return field descriptors
     */
    List<PiFieldDescriptor> fields();
}
