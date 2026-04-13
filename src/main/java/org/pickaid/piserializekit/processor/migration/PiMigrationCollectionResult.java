package org.pickaid.piserializekit.processor.migration;

import java.util.List;

public record PiMigrationCollectionResult(List<PiMigrationStepSpec> steps, PiMigrationValidationFailure failure) {
}
