package org.pickaid.piserializekit.runtime.schema;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiFieldDeltaMode;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiSyncModel(id = "test:generated_merge_state", version = 1)
public final class GeneratedMergeState {
    @PiField(id = "checkpoints", sync = PiSyncScope.TRACKING, persist = true, delta = PiFieldDeltaMode.MERGE_SET)
    public final Set<ResourceLocation> checkpoints = new LinkedHashSet<>();

    @PiField(id = "weights", sync = PiSyncScope.TRACKING, persist = true, delta = PiFieldDeltaMode.MERGE_MAP)
    public final Map<String, Integer> weights = new LinkedHashMap<>();
}
