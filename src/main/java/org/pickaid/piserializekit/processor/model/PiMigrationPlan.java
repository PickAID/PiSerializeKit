package org.pickaid.piserializekit.processor.model;

import java.util.List;
import org.pickaid.piserializekit.processor.migration.PiMigrationStepSpec;

public record PiMigrationPlan(List<PiMigrationStepSpec> steps) {
    public boolean present() {
        return !steps.isEmpty();
    }
}
