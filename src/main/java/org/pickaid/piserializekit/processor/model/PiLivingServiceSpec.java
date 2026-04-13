package org.pickaid.piserializekit.processor.model;

public record PiLivingServiceSpec(
        String namespace,
        String path,
        String serviceSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
