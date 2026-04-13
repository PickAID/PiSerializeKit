package org.pickaid.piserializekit.processor.migration;

import javax.lang.model.element.ExecutableElement;

public record PiMigrationStepSpec(int fromVersion, int toVersion, String methodName, ExecutableElement methodElement) {
}
