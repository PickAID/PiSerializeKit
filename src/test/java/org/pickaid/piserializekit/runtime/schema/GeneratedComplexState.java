package org.pickaid.piserializekit.runtime.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiSyncModel(id = "test:generated_complex_state", version = 1)
public final class GeneratedComplexState {
    @PiField(id = "names", sync = PiSyncScope.TRACKING, persist = true)
    public final List<String> names = new ArrayList<>();

    @PiField(id = "counts", sync = PiSyncScope.TRACKING, persist = true)
    public final Map<String, Integer> counts = new LinkedHashMap<>();

    @PiField(id = "child", sync = PiSyncScope.TRACKING, persist = true)
    public GeneratedChildState child = new GeneratedChildState();

    @PiField(id = "label", sync = PiSyncScope.OWNER, persist = true, serializer = TrimmedStringCodec.class)
    public String label = "fallback";
}
