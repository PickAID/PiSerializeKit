package org.pickaid.piserializekit.processor.model;

import java.util.List;

public record PiPacketSpec(
        String namespace,
        String path,
        int version,
        PiPacketDirectionSpec direction,
        List<PiFieldSpec> fields,
        PiMigrationPlan migrations
) {
}
