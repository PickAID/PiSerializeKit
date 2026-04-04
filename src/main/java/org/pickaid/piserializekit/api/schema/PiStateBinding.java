package org.pickaid.piserializekit.api.schema;

import java.util.List;

public interface PiStateBinding<T> extends PiSyncSchema<T> {
    Class<T> stateType();

    T newState();

    List<PiFieldDescriptor> fields();
}
