package org.pickaid.piserializekit.processor.model;

public record PiLevelServiceSpec(
        String namespace,
        String path,
        String serviceSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
