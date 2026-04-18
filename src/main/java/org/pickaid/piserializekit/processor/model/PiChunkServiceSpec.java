package org.pickaid.piserializekit.processor.model;

public record PiChunkServiceSpec(
        String namespace,
        String path,
        String serviceSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
