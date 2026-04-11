package org.pickaid.piserializekit.runtime.schema;

import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiSyncModel(id = "test:generated_projection_state", version = 1)
public final class GeneratedProjectionState {
    @PiField(id = "phase", sync = PiSyncScope.GLOBAL, persist = true)
    public int phase;

    @PiField(id = "reward_label", sync = PiSyncScope.OWNER, persist = true)
    public String rewardLabel = "fallback";

    @PiField(id = "menu_page", sync = PiSyncScope.MENU, persist = false)
    public int menuPage;
}
