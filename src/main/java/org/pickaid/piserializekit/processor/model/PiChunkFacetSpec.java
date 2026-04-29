package org.pickaid.piserializekit.processor.model;

public record PiChunkFacetSpec(
        String namespace,
        String path,
        String facetSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
