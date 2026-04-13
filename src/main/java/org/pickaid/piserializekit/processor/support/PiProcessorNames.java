package org.pickaid.piserializekit.processor.support;

public final class PiProcessorNames {
    private PiProcessorNames() {
    }

    public static String camelToSnake(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current)) {
                boolean needsSeparator = i > 0
                        && (Character.isLowerCase(value.charAt(i - 1))
                        || Character.isDigit(value.charAt(i - 1))
                        || i + 1 < value.length() && Character.isLowerCase(value.charAt(i + 1)));
                if (needsSeparator) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static String constantName(String simpleName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char current = simpleName.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            char current = namespace.charAt(i);
            if (!isLowercaseResourceChar(current) && current != '.' && current != '-') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (!isLowercaseResourceChar(current) && current != '.' && current != '-' && current != '/') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPayloadKey(String value) {
        return !value.isEmpty() && isValidPath(value);
    }

    public static boolean isReservedPayloadKey(String value) {
        return value.startsWith("__pi_");
    }

    private static boolean isLowercaseResourceChar(char current) {
        return current == '_' || current >= 'a' && current <= 'z' || current >= '0' && current <= '9';
    }
}
