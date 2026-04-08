package org.pickaid.piserializekit.runtime.schema;

import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiSyncModel(id = "test:generated_child_state", version = 1)
public final class GeneratedChildState {
    @PiField(id = "value", sync = PiSyncScope.TRACKING, persist = true)
    public int value;
}
