package org.pickaid.piserializekit.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PiSyncModelProcessorStructureTest {
    @Test
    void syncModelProcessorStaysBelowMaintainabilityLineBudget() throws IOException {
        Path processor = Path.of("src/main/java/org/pickaid/piserializekit/processor/PiSyncModelProcessor.java");
        long lineCount = Files.lines(processor).count();

        assertTrue(
                lineCount <= 1200,
                () -> "PiSyncModelProcessor should stay at or below 1200 lines, but was " + lineCount
        );
    }
}
