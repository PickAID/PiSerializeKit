package org.pickaid.piserializekit.processor.model;

public record PiLevelFacetSpec(
        String namespace,
        String path,
        String facetSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
