package org.pickaid.piserializekit.api.schema;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Header-aware field snapshot used for low-allocation dirty diffing.
 */
public final class PiStateSnapshot {
    private final ResourceLocation schemaId;
    private final int version;
    private final Tag[] fieldTags;

    /**
     * Creates one immutable snapshot.
     *
     * @param schemaId schema identity
     * @param version schema version
     * @param fieldTags captured field tags in binding order
     */
    public PiStateSnapshot(ResourceLocation schemaId, int version, Tag[] fieldTags) {
        this.schemaId = Objects.requireNonNull(schemaId, "schemaId");
        this.version = version;
        Objects.requireNonNull(fieldTags, "fieldTags");
        this.fieldTags = new Tag[fieldTags.length];
        for (int i = 0; i < fieldTags.length; i++) {
            this.fieldTags[i] = copy(fieldTags[i]);
        }
    }

    /**
     * Returns the schema identity captured by this snapshot.
     *
     * @return schema id
     */
    public ResourceLocation schemaId() {
        return schemaId;
    }

    /**
     * Returns the captured schema version.
     *
     * @return schema version
     */
    public int version() {
        return version;
    }

    /**
     * Returns the number of captured fields.
     *
     * @return captured field count
     */
    public int fieldCount() {
        return fieldTags.length;
    }

    /**
     * Returns a defensive copy of one captured field tag.
     *
     * @param index field index in binding order
     * @return copied field tag, or null
     */
    public Tag tag(int index) {
        return copy(fieldTags[index]);
    }

    /**
     * Returns whether this snapshot belongs to the provided binding.
     *
     * @param binding binding to compare against
     * @return true when schema id, version, and field count match
     */
    public boolean matches(PiStateBinding<?> binding) {
        Objects.requireNonNull(binding, "binding");
        return schemaId.equals(binding.schemaId())
                && version == binding.version()
                && fieldCount() == binding.fields().size();
    }

    /**
     * Returns whether the current field tag matches the captured baseline.
     *
     * @param index field index in binding order
     * @param current current field tag
     * @return true when unchanged
     */
    public boolean sameField(int index, Tag current) {
        return Objects.equals(fieldTags[index], current);
    }

    private static Tag copy(Tag tag) {
        return tag == null ? null : tag.copy();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PiStateSnapshot snapshot)) {
            return false;
        }
        return version == snapshot.version
                && schemaId.equals(snapshot.schemaId)
                && Arrays.equals(fieldTags, snapshot.fieldTags);
    }

    @Override
    public int hashCode() {
        int result = schemaId.hashCode();
        result = 31 * result + version;
        result = 31 * result + Arrays.hashCode(fieldTags);
        return result;
    }
}
