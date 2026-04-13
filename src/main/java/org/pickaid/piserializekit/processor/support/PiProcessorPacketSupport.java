package org.pickaid.piserializekit.processor.support;

public final class PiProcessorPacketSupport {
    private static final String[] INFERRED_PACKET_SUFFIXES = {
            "Packet",
            "Request",
            "Payload",
            "ToClient",
            "ToServer"
    };

    private PiProcessorPacketSupport() {
    }

    public static String validateDeclaredPacketId(boolean declared, String id) {
        return declared && (id == null || id.isBlank())
                ? "@PiPacket.id must be non-blank when declared"
                : null;
    }

    public static String validateDeclaredPacketNamespace(boolean declared, String namespace) {
        return declared && (namespace == null || namespace.isBlank())
                ? "@PiPacket.namespace must be non-blank when declared"
                : null;
    }

    public static String validateDeclaredPacketPath(boolean declared, String path) {
        return declared && (path == null || path.isBlank())
                ? "@PiPacket.path must be non-blank when declared"
                : null;
    }

    public static String validatePacketIdentityCombination(boolean declaredId, boolean declaredNamespace, boolean declaredPath) {
        return declaredId && (declaredNamespace || declaredPath)
                ? "@PiPacket.id cannot be combined with @PiPacket.namespace or @PiPacket.path"
                : null;
    }

    public static String validatePacketNamespace(String namespace) {
        return PiProcessorNames.isValidNamespace(namespace)
                ? null
                : invalidPacketNamespaceMessage(namespace);
    }

    public static String validatePacketPath(String path) {
        return !path.isEmpty() && PiProcessorNames.isValidPath(path)
                ? null
                : invalidPacketPathMessage(path);
    }

    public static String invalidPacketNamespaceMessage(String namespace) {
        return "@PiPacket.namespace must be a valid resource namespace: " + namespace;
    }

    public static String invalidPacketPathMessage(String path) {
        return "@PiPacket.path must resolve to a valid resource path: " + path;
    }

    public static String invalidPackageNamespaceMessage(String namespace) {
        return "@PiPacketNamespace value must be a valid resource namespace: " + namespace;
    }

    public static String missingPacketNamespaceMessage() {
        return "@PiPacket requires a namespace via @PiPacket(namespace = ...), @PiPacket(id = ...), or package-info @PiPacketNamespace(...)";
    }

    public static String resolvePacketPath(String explicitPath, String simpleName) {
        return explicitPath == null || explicitPath.isBlank()
                ? inferPacketPath(simpleName)
                : explicitPath;
    }

    static String inferPacketPath(String simpleName) {
        String trimmed = trimInferredPacketSuffixes(simpleName);
        return trimmed.isEmpty() ? PiProcessorNames.camelToSnake(simpleName) : PiProcessorNames.camelToSnake(trimmed);
    }

    private static String trimInferredPacketSuffixes(String simpleName) {
        String current = simpleName;
        boolean changed;
        do {
            changed = false;
            for (String suffix : INFERRED_PACKET_SUFFIXES) {
                if (current.endsWith(suffix) && current.length() > suffix.length()) {
                    current = current.substring(0, current.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return current;
    }
}
