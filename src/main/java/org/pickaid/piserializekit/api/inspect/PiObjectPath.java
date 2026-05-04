package org.pickaid.piserializekit.api.inspect;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable path used while walking an inspected object graph.
 *
 * <p>The root path is {@code $}. Fields append with dot notation when possible
 * ({@code $.spell.damage}); list and array elements append as indexes
 * ({@code $.actions[0]}); map values append with a quoted key
 * ({@code $.weights["minecraft:zombie"]}).</p>
 */
public final class PiObjectPath {
    private static final Pattern SIMPLE_FIELD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final PiObjectPath ROOT = new PiObjectPath("$");

    private final String value;

    private PiObjectPath(String value) {
        this.value = value;
    }

    /**
     * Returns the root path.
     *
     * @return root path
     */
    public static PiObjectPath root() {
        return ROOT;
    }

    /**
     * Appends a field segment.
     *
     * @param name field name
     * @return child path
     */
    public PiObjectPath field(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("field name must be non-blank");
        }
        if (SIMPLE_FIELD.matcher(name).matches()) {
            return new PiObjectPath(value + "." + name);
        }
        return new PiObjectPath(value + "[\"" + escape(name) + "\"]");
    }

    /**
     * Appends a list or array index segment.
     *
     * @param index element index
     * @return child path
     */
    public PiObjectPath index(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        return new PiObjectPath(value + "[" + index + "]");
    }

    /**
     * Appends a map key segment.
     *
     * @param key map key
     * @return child path
     */
    public PiObjectPath key(Object key) {
        return new PiObjectPath(value + "[\"" + escape(String.valueOf(key)) + "\"]");
    }

    /**
     * Returns the rendered path.
     *
     * @return path string
     */
    public String value() {
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PiObjectPath path)) {
            return false;
        }
        return value.equals(path.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
