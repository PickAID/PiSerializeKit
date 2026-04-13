package org.pickaid.piserializekit.api.schema;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

/**
 * Runtime binding for one generated sync schema.
 *
 * @param <T> state type
 */
public interface PiStateBinding<T> extends PiSyncSchema<T> {
    /**
     * Returns the schema identifier used for payload headers and diagnostics.
     *
     * @return schema id
     */
    ResourceLocation schemaId();

    /**
     * Returns the schema identifier as the exact header string used in payloads.
     *
     * @return schema id string
     */
    default String schemaIdString() {
        return schemaId().toString();
    }

    /**
     * Returns the generated schema version.
     *
     * @return schema version
     */
    int version();

    /**
     * Returns the bound state type.
     *
     * @return state class
     */
    Class<T> stateType();

    /**
     * Creates a fresh state instance.
     *
     * @return new state
     */
    T newState();

    /**
     * Returns the declared fields for the schema.
     *
     * @return field descriptors
     */
    List<PiFieldDescriptor> fields();

    /**
     * Returns binding-local schema upgrade steps for older payloads.
     *
     * @return ordered migration steps
     */
    default List<PiSchemaMigration> migrations() {
        return List.of();
    }

    /**
     * Serializes the persisted subset of the state.
     *
     * @param self state to serialize
     * @return persisted payload
     */
    default CompoundTag savePersisted(T self) {
        return saveProjection(self, PiProjections.persisted());
    }

    /**
     * Serializes the default client-visible subset of the state.
     *
     * @param self state to serialize
     * @return client-visible payload
     */
    @Override
    default CompoundTag saveClientView(T self) {
        return saveProjection(self, PiProjections.client());
    }

    /**
     * Loads the persisted subset of the state.
     *
     * @param self state to mutate
     * @param tag persisted payload
     * @param context decode context
     */
    default void loadPersisted(T self, CompoundTag tag, PiDecodeContext context) {
        CompoundTag payload = PiSchemaSupport.preparePayload(tag, context, this, PiSchemaPayloadKind.PERSISTED);
        if (payload == null) {
            return;
        }
        CompoundTag merged = saveFull(self).copy();
        for (PiFieldDescriptor descriptor : fields()) {
            if (!descriptor.persist()) {
                continue;
            }
            Tag value = payload.get(descriptor.key().id());
            if (value != null) {
                merged.put(descriptor.key().id(), value.copy());
            }
        }
        loadFull(self, merged, context);
    }

    /**
     * Serializes the requested field projection.
     *
     * @param self state to serialize
     * @param projection requested projection
     * @return projected payload
     */
    default CompoundTag saveProjection(T self, PiProjection projection) {
        CompoundTag tag = saveFull(self).copy();
        pruneFields(tag, projection);
        return tag;
    }

    /**
     * Serializes a projection-filtered delta.
     *
     * @param self state to serialize
     * @param dirtySet dirty field set
     * @param projection requested projection
     * @return projected delta
     */
    default CompoundTag writeDelta(T self, PiDirtySet dirtySet, PiProjection projection) {
        PiDirtySet filtered = new PiDirtySet();
        for (PiFieldDescriptor descriptor : fields()) {
            if (projection.includes(descriptor) && dirtySet.contains(descriptor.key())) {
                filtered.mark(descriptor.key());
            }
        }
        return writeDelta(self, filtered);
    }

    /**
     * Serializes a projection-filtered delta from bit-backed dirty tracking.
     *
     * @param self state to serialize
     * @param dirtyBits dirty field bits
     * @param projection requested projection
     * @return projected delta
     */
    default CompoundTag writeDelta(T self, PiDirtyBits dirtyBits, PiProjection projection) {
        return PiDirtyPlans.forProjection(this, dirtyBits, projection).writeDelta(self);
    }

    /**
     * Serializes a delta restricted to the default client-visible projection.
     *
     * @param self state to serialize
     * @param dirtySet dirty field set
     * @return client-visible delta
     */
    default CompoundTag writeClientDelta(T self, PiDirtySet dirtySet) {
        return writeDelta(self, dirtySet, PiProjections.client());
    }

    /**
     * Serializes a delta restricted to persisted fields.
     *
     * @param self state to serialize
     * @param dirtySet dirty field set
     * @return persisted delta
     */
    default CompoundTag writePersistedDelta(T self, PiDirtySet dirtySet) {
        return writeDelta(self, dirtySet, PiProjections.persisted());
    }

    /**
     * Captures a field snapshot for later dirty diffing.
     *
     * @param self state to snapshot
     * @return state snapshot
     */
    default PiStateSnapshot snapshot(T self) {
        CompoundTag root = saveFull(self);
        Tag[] tags = new Tag[fields().size()];
        for (PiFieldDescriptor descriptor : fields()) {
            tags[descriptor.key().index()] = copy(root.get(descriptor.key().id()));
        }
        return new PiStateSnapshot(schemaId(), version(), tags);
    }

    /**
     * Diffs the current state against a baseline snapshot.
     *
     * @param self current state
     * @param snapshot baseline snapshot
     * @return dirty bits
     */
    default PiDirtyBits diff(T self, PiStateSnapshot snapshot) {
        if (!snapshot.matches(this)) {
            throw new IllegalArgumentException("PiStateSnapshot does not match binding " + schemaId());
        }
        CompoundTag root = saveFull(self);
        PiDirtyBits bits = new PiDirtyBits();
        for (PiFieldDescriptor descriptor : fields()) {
            int index = descriptor.key().index();
            if (!snapshot.sameField(index, root.get(descriptor.key().id()))) {
                bits.mark(descriptor.key());
            }
        }
        return bits;
    }

    private static Tag copy(Tag tag) {
        return tag == null ? null : tag.copy();
    }

    private void pruneFields(CompoundTag tag, PiProjection projection) {
        for (PiFieldDescriptor descriptor : fields()) {
            if (!projection.includes(descriptor)) {
                tag.remove(descriptor.key().id());
            }
        }
    }
}
