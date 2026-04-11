package org.pickaid.piserializekit.api.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.runtime.schema.PiSchemaSupport;

class PiDirtyBitsTest {
    private static final PiFieldKey TRACKING = new PiFieldKey(0, "tracking");
    private static final PiFieldKey OWNER = new PiFieldKey(1, "owner");
    private static final PiFieldKey GLOBAL = new PiFieldKey(2, "global");
    private static final PiFieldDescriptor TRACKING_FIELD = new PiFieldDescriptor(TRACKING, PiSyncScope.TRACKING, true);
    private static final PiFieldDescriptor OWNER_FIELD = new PiFieldDescriptor(OWNER, PiSyncScope.OWNER, true);
    private static final PiFieldDescriptor GLOBAL_FIELD = new PiFieldDescriptor(GLOBAL, PiSyncScope.GLOBAL, true);

    private static final class DirtyState {
        private int tracking;
        private int owner;
        private int global;
    }

    private static final class DirtyBinding implements PiStateBinding<DirtyState> {
        @Override
        public ResourceLocation schemaId() {
            return ResourceLocation.fromNamespaceAndPath("test", "dirty_bits");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<DirtyState> stateType() {
            return DirtyState.class;
        }

        @Override
        public DirtyState newState() {
            return new DirtyState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(TRACKING_FIELD, OWNER_FIELD, GLOBAL_FIELD);
        }

        @Override
        public CompoundTag saveFull(DirtyState self) {
            return PiSchemaSupport.tagWithHeader(schemaId(), version());
        }

        @Override
        public void loadFull(DirtyState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(DirtyState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(DirtyState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(schemaId(), version());
            if (dirtySet.contains(TRACKING)) {
                tag.putInt("tracking", self.tracking);
            }
            if (dirtySet.contains(OWNER)) {
                tag.putInt("owner", self.owner);
            }
            if (dirtySet.contains(GLOBAL)) {
                tag.putInt("global", self.global);
            }
            return tag;
        }

        @Override
        public void applyDelta(DirtyState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final DirtyBinding BINDING = new DirtyBinding();

    @Test
    void dirtyBitsTrackFieldsByIndexAcrossKeyAndDescriptorAccess() {
        PiDirtyBits bits = new PiDirtyBits()
                .mark(OWNER_FIELD)
                .mark(GLOBAL);

        assertTrue(bits.contains(OWNER));
        assertTrue(bits.contains(GLOBAL_FIELD));
        assertFalse(bits.contains(TRACKING));

        bits.clear(GLOBAL_FIELD);

        assertFalse(bits.contains(GLOBAL));
        assertFalse(bits.isEmpty());
    }

    @Test
    void dirtySetCanProjectIntoDirtyBits() {
        PiDirtyBits bits = new PiDirtySet()
                .mark(TRACKING)
                .mark(GLOBAL)
                .toBits();

        assertTrue(bits.contains(TRACKING_FIELD));
        assertTrue(bits.contains(GLOBAL));
        assertFalse(bits.contains(OWNER));
    }

    @Test
    void dirtyPlansProjectFromBitsUsingBindingOrderAndRequestedScopes() {
        PiDirtyBits bits = new PiDirtyBits()
                .mark(GLOBAL)
                .mark(TRACKING)
                .mark(OWNER);

        PiDirtyPlan<DirtyState> plan = PiDirtyPlans.forBits(BINDING, bits, PiSyncScope.OWNER, PiSyncScope.GLOBAL);

        assertEquals(List.of(OWNER_FIELD, GLOBAL_FIELD), plan.descriptors());
    }
}
