package org.pickaid.piserializekit.runtime.schema;

import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiFieldDeltaMode;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiSyncModel(id = "test:generated_nested_delta_state", version = 1)
public final class GeneratedNestedDeltaState {
    @PiField(id = "child", sync = PiSyncScope.TRACKING, persist = true, delta = PiFieldDeltaMode.NESTED_UPDATE)
    public GeneratedChildState child = new GeneratedChildState();
}
