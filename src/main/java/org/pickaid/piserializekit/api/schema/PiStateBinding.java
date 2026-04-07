package org.pickaid.piserializekit.api.schema;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public interface PiStateBinding<T> extends PiSyncSchema<T> {
    ResourceLocation schemaId();

    int version();

    Class<T> stateType();

    T newState();

    List<PiFieldDescriptor> fields();
}
