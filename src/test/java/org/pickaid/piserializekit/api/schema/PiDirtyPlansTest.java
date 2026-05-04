package org.pickaid.piserializekit.api.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

class PiDirtyPlansTest {
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
            return new ResourceLocation("test", "dirty_plan");
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
            return PiSchemaSupport.tagWithHeader(
                    schemaId(),
                    version(),
                    PiSchemaSupport.putField("tracking", PiSerializers.INT, self.tracking),
                    PiSchemaSupport.putField("owner", PiSerializers.INT, self.owner),
                    PiSchemaSupport.putField("global", PiSerializers.INT, self.global)
            );
        }

        @Override
        public void loadFull(DirtyState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag writeDelta(DirtyState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(schemaId(), version());
            if (dirtySet.contains(TRACKING)) {
                tag.put("tracking", PiSchemaSupport.putField("tracking", PiSerializers.INT, self.tracking).getSecond());
            }
            if (dirtySet.contains(OWNER)) {
                tag.put("owner", PiSchemaSupport.putField("owner", PiSerializers.INT, self.owner).getSecond());
            }
            if (dirtySet.contains(GLOBAL)) {
                tag.put("global", PiSchemaSupport.putField("global", PiSerializers.INT, self.global).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(DirtyState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final DirtyBinding BINDING = new DirtyBinding();

    @Test
    void scopePlanSelectsDirtyFieldsInBindingOrder() {
        PiDirtySet dirty = new PiDirtySet()
                .mark(GLOBAL)
                .mark(TRACKING)
                .mark(OWNER);

        PiDirtyPlan<DirtyState> plan = PiDirtyPlans.forScopes(BINDING, dirty, PiSyncScope.OWNER, PiSyncScope.GLOBAL);

        assertFalse(plan.isEmpty());
        assertEquals(List.of(OWNER_FIELD, GLOBAL_FIELD), plan.descriptors());
        assertEquals(Set.of(OWNER, GLOBAL), plan.keys());
    }

    @Test
    void scopePlanWritesOnlyRequestedScopeDelta() {
        DirtyState state = new DirtyState();
        state.tracking = 4;
        state.owner = 9;
        state.global = 12;
        PiDirtySet dirty = new PiDirtySet()
                .mark(TRACKING)
                .mark(OWNER)
                .mark(GLOBAL);

        PiDirtyPlan<DirtyState> plan = PiDirtyPlans.forScopes(BINDING, dirty, PiSyncScope.TRACKING, PiSyncScope.GLOBAL);
        CompoundTag delta = plan.writeDelta(state);

        assertTrue(delta.contains(PiSchemaSupport.SCHEMA_ID_KEY));
        assertTrue(delta.contains("tracking"));
        assertFalse(delta.contains("owner"));
        assertTrue(delta.contains("global"));
    }

    @Test
    void scopePlanReportsEmptyWhenNoRequestedScopeIsDirty() {
        PiDirtySet dirty = new PiDirtySet().mark(OWNER);

        PiDirtyPlan<DirtyState> plan = PiDirtyPlans.forScopes(BINDING, dirty, PiSyncScope.TRACKING);

        assertTrue(plan.isEmpty());
        assertTrue(plan.descriptors().isEmpty());
        assertTrue(plan.keys().isEmpty());
    }

    @Test
    void projectionPlanFiltersDirtyFieldsByProjectionRules() {
        PiDirtySet dirty = new PiDirtySet()
                .mark(TRACKING)
                .mark(OWNER)
                .mark(GLOBAL);

        PiDirtyPlan<DirtyState> plan = PiDirtyPlans.forProjection(BINDING, dirty, PiProjections.client());

        assertEquals(List.of(TRACKING_FIELD, GLOBAL_FIELD), plan.descriptors());
        assertEquals(Set.of(TRACKING, GLOBAL), plan.keys());
    }

    @Test
    void defaultClientViewUsesClientProjection() {
        DirtyState state = new DirtyState();
        state.tracking = 4;
        state.owner = 9;
        state.global = 12;

        CompoundTag client = BINDING.saveClientView(state);

        assertTrue(client.contains(PiSchemaSupport.SCHEMA_ID_KEY));
        assertTrue(client.contains("tracking"));
        assertFalse(client.contains("owner"));
        assertTrue(client.contains("global"));
    }

    @Test
    void defaultClientDeltaUsesClientProjection() {
        DirtyState state = new DirtyState();
        state.tracking = 4;
        state.owner = 9;
        state.global = 12;
        PiDirtySet dirty = new PiDirtySet()
                .mark(TRACKING)
                .mark(OWNER)
                .mark(GLOBAL);

        CompoundTag delta = BINDING.writeClientDelta(state, dirty);

        assertTrue(delta.contains(PiSchemaSupport.SCHEMA_ID_KEY));
        assertTrue(delta.contains("tracking"));
        assertFalse(delta.contains("owner"));
        assertTrue(delta.contains("global"));
    }

    @Test
    void dirtySetSupportsDescriptorBasedMutationAndQueries() {
        PiDirtySet dirty = new PiDirtySet()
                .mark(TRACKING_FIELD)
                .mark(OWNER_FIELD);

        assertTrue(dirty.contains(TRACKING_FIELD));
        assertTrue(dirty.contains(OWNER_FIELD));
        assertFalse(dirty.contains(GLOBAL_FIELD));

        dirty.clear(TRACKING_FIELD);

        assertFalse(dirty.contains(TRACKING_FIELD));
        assertTrue(dirty.contains(OWNER_FIELD));
        assertEquals(Set.of(OWNER), dirty.keys());
    }
}
