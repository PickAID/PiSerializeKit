package org.pickaid.piserializekit.processor.support;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;

public final class PiProcessorSchemaSupport {
    private PiProcessorSchemaSupport() {
    }

    public static PiResolvedResourceLocation resolveExplicitResourceLocation(String id) {
        int delimiter = id.indexOf(':');
        if (delimiter <= 0 || delimiter != id.lastIndexOf(':') || delimiter == id.length() - 1) {
            return null;
        }
        String namespace = id.substring(0, delimiter);
        String path = id.substring(delimiter + 1);
        if (!PiProcessorNames.isValidNamespace(namespace) || !PiProcessorNames.isValidPath(path)) {
            return null;
        }
        return new PiResolvedResourceLocation(id, namespace, path);
    }

    public static String invalidResourceLocationMessage(String label, String id) {
        return label + ": " + id;
    }

    public static String validateAfterDecodeMethod(ExecutableElement method) {
        if (method.getModifiers().contains(Modifier.PRIVATE)
                || method.getModifiers().contains(Modifier.STATIC)
                || !method.getParameters().isEmpty()
                || method.getReturnType().getKind() != TypeKind.VOID) {
            return "@PiAfterDecode methods must be non-static, non-private, no-arg, and return void";
        }
        return null;
    }
}
