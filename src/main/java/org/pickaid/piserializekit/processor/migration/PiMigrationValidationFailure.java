package org.pickaid.piserializekit.processor.migration;

import javax.lang.model.element.Element;

public record PiMigrationValidationFailure(Element element, String message) {
}
