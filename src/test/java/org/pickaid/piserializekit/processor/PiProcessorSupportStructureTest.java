package org.pickaid.piserializekit.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PiProcessorSupportStructureTest {
    @Test
    void packetAuthoringSupportStaysBelowMaintainabilityLineBudget() throws IOException {
        assertLineBudget(
                "src/main/java/org/pickaid/piserializekit/processor/support/PiProcessorPacketAuthoringSupport.java",
                320
        );
    }

    @Test
    void fieldAuthoringSupportStaysBelowMaintainabilityLineBudget() throws IOException {
        assertLineBudget(
                "src/main/java/org/pickaid/piserializekit/processor/support/PiProcessorFieldAuthoringSupport.java",
                320
        );
    }

    @Test
    void annotationSupportStaysBelowMaintainabilityLineBudget() throws IOException {
        assertLineBudget(
                "src/main/java/org/pickaid/piserializekit/processor/support/PiProcessorAnnotationSupport.java",
                180
        );
    }

    private void assertLineBudget(String relativePath, long maxLines) throws IOException {
        Path sourceFile = Path.of(relativePath);
        long lineCount = Files.lines(sourceFile).count();

        assertTrue(
                lineCount <= maxLines,
                () -> relativePath + " should stay at or below " + maxLines + " lines, but was " + lineCount
        );
    }
}
