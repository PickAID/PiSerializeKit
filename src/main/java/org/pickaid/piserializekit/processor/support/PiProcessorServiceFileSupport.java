package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Shared writer for generated {@code META-INF/services} entries emitted by the processor.
 */
public final class PiProcessorServiceFileSupport {
    private PiProcessorServiceFileSupport() {
    }

    public static void writeServiceFile(
            ProcessingEnvironment processingEnv,
            Set<String> providerTypes,
            String serviceType,
            String failureMessage
    ) {
        if (providerTypes.isEmpty()) {
            return;
        }
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/" + serviceType
            );
            try (Writer writer = file.openWriter()) {
                for (String providerType : providerTypes) {
                    writer.write(providerType);
                    writer.write("\n");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }
}
