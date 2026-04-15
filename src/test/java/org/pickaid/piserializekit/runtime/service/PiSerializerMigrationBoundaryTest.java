package org.pickaid.piserializekit.runtime.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PiSerializerMigrationBoundaryTest {
    @Test
    void serializerLayersDoNotImportFriendlyByteBufDirectly() throws IOException {
        List<Path> files = List.of(
                Path.of("src/main/java/org/pickaid/piserializekit/api/service/PiSerializers.java"),
                Path.of("src/main/java/org/pickaid/piserializekit/runtime/service/PiBuiltInSerializers.java")
        );
        List<Path> leaked = files.stream()
                .filter(PiSerializerMigrationBoundaryTest::importsFriendlyByteBuf)
                .sorted(Comparator.comparing(Path::toString))
                .toList();

        assertTrue(
                leaked.isEmpty(),
                () -> "Serializer layers should depend on the packet buffer adapter instead of FriendlyByteBuf directly: " + leaked
        );
    }

    private static boolean importsFriendlyByteBuf(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.anyMatch(line -> line.equals("import net.minecraft.network.FriendlyByteBuf;"));
        } catch (IOException exception) {
            throw new AssertionError("Failed to inspect " + path, exception);
        }
    }
}
