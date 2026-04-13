package org.pickaid.piserializekit.processor.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PiProcessorSourceSupportTest {
    @Test
    void generatedSourceTargetOmitsPackageDeclarationForUnnamedPackage() {
        PiProcessorSourceSupport.GeneratedSourceTarget target =
                new PiProcessorSourceSupport.GeneratedSourceTarget("", "Example_Generated", "Example_Generated");

        assertEquals("", target.packageDeclaration());
    }

    @Test
    void generatedSourceTargetFormatsNamedPackageDeclaration() {
        PiProcessorSourceSupport.GeneratedSourceTarget target =
                new PiProcessorSourceSupport.GeneratedSourceTarget("example.generated", "Example_Generated", "example.generated.Example_Generated");

        assertEquals("package example.generated;\n\n", target.packageDeclaration());
    }
}
