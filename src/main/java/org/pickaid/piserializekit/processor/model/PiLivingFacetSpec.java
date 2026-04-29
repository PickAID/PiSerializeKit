package org.pickaid.piserializekit.processor.model;

public record PiLivingFacetSpec(
        String namespace,
        String path,
        String facetSimpleName,
        String stateQualifiedName,
        String stateSimpleName
) {
}
