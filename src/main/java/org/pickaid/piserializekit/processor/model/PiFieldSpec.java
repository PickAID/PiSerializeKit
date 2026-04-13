package org.pickaid.piserializekit.processor.model;

public record PiFieldSpec(
        int index,
        String constantName,
        String schemaConstantName,
        String fieldName,
        String id,
        String valueType,
        PiRawKind rawKind,
        String syncScope,
        boolean persist,
        String serializerExpression,
        PiFieldAccessStrategy accessStrategy,
        String deltaMode,
        boolean nestedSyncModel
) {
}
