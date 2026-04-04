package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDirtySet;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSyncSchema;

class PiSchemaRuntimeTest {
    private static final PiFieldKey PLAYERS = new PiFieldKey(0, "players");
    private static final PiFieldKey COST = new PiFieldKey(1, "cost");
    private static final String SCHEMA_ID = "test.TrialState";
    private static final int VERSION = 3;

    private static final class TestState {
        private final List<String> players = new ArrayList<>();
        private long cost;
    }

    private static final class TestSchema implements PiSyncSchema<TestState> {
        @Override
        public CompoundTag saveFull(TestState self) {
            return PiSchemaSupport.tagWithHeader(
                    SCHEMA_ID,
                    VERSION,
                    PiSchemaSupport.putStringList("players", self.players),
                    PiSchemaSupport.putLong("cost", self.cost)
            );
        }

        @Override
        public void loadFull(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {
                return;
            }
            self.players.clear();
            self.players.addAll(PiSchemaSupport.getStringList(tag, "players", context));
            self.cost = PiSchemaSupport.getLong(tag, "cost", context, 0L);
        }

        @Override
        public CompoundTag saveClientView(TestState self) {
            return PiSchemaSupport.tagWithHeader(
                    SCHEMA_ID,
                    VERSION,
                    PiSchemaSupport.putStringList("players", self.players)
            );
        }

        @Override
        public CompoundTag writeDelta(TestState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);
            if (dirtySet.contains(PLAYERS)) {
                tag.put("players", PiSchemaSupport.putStringList("players", self.players).getSecond());
            }
            if (dirtySet.contains(COST)) {
                tag.put("cost", PiSchemaSupport.putLong("cost", self.cost).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {
                return;
            }
            if (tag.contains("players")) {
                self.players.clear();
                self.players.addAll(PiSchemaSupport.getStringList(tag, "players", context));
            }
            if (tag.contains("cost")) {
                self.cost = PiSchemaSupport.getLong(tag, "cost", context, self.cost);
            }
        }
    }

    @Test
    void fullSaveLoadRestoresPlayersAndCost() {
        TestState state = new TestState();
        state.players.add("alice");
        state.players.add("bob");
        state.cost = 42L;
        TestSchema schema = new TestSchema();
        TestState restored = new TestState();

        schema.loadFull(restored, schema.saveFull(state), PiDecodeContext.strict());

        assertEquals(List.of("alice", "bob"), restored.players);
        assertEquals(42L, restored.cost);
    }

    @Test
    void clientViewContainsPlayersButNotCost() {
        TestState state = new TestState();
        state.players.add("alice");
        state.cost = 42L;
        TestSchema schema = new TestSchema();

        CompoundTag client = schema.saveClientView(state);

        assertTrue(client.contains("players"));
        assertFalse(client.contains("cost"));
        assertEquals(SCHEMA_ID, client.getString("__pi_schema"));
        assertEquals(VERSION, client.getInt("__pi_version"));
    }

    @Test
    void deltaWriteAndApplyHonorDirtyKeys() {
        TestSchema schema = new TestSchema();
        TestState state = new TestState();
        state.players.add("alice");
        state.cost = 7L;

        TestState restored = new TestState();
        restored.players.add("stale");
        restored.cost = 1L;

        CompoundTag playersDelta = schema.writeDelta(state, new PiDirtySet().mark(PLAYERS));
        assertEquals(SCHEMA_ID, playersDelta.getString("__pi_schema"));
        assertEquals(VERSION, playersDelta.getInt("__pi_version"));

        schema.applyDelta(restored, playersDelta, PiDecodeContext.strict());
        assertEquals(List.of("alice"), restored.players);
        assertEquals(1L, restored.cost);

        schema.applyDelta(restored, schema.writeDelta(state, new PiDirtySet().mark(COST)), PiDecodeContext.strict());
        assertEquals(List.of("alice"), restored.players);
        assertEquals(7L, restored.cost);
    }

    @Test
    void missingFieldsRecordIssuesAndUseDefaults() {
        TestSchema schema = new TestSchema();
        TestState restored = new TestState();
        PiDecodeContext context = PiDecodeContext.strict();

        CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);
        schema.loadFull(restored, tag, context);

        assertTrue(restored.players.isEmpty());
        assertEquals(0L, restored.cost);
        assertEquals(2, context.result().issues().size());
        assertEquals("players", context.result().issues().get(0).path());
        assertEquals("missing list", context.result().issues().get(0).message());
        assertFalse(context.result().issues().get(0).fatal());
        assertEquals("cost", context.result().issues().get(1).path());
        assertEquals("missing long", context.result().issues().get(1).message());
        assertFalse(context.result().issues().get(1).fatal());
        assertFalse(context.result().hasFatal());
    }

    @Test
    void headerMismatchStopsDecodeAndMarksFatal() {
        TestSchema schema = new TestSchema();
        TestState restored = new TestState();
        restored.players.add("keep");
        restored.cost = 9L;

        CompoundTag tag = schema.saveFull(new TestState());
        tag.putString("__pi_schema", "other.State");

        PiDecodeContext context = PiDecodeContext.strict();
        schema.loadFull(restored, tag, context);

        assertEquals(List.of("keep"), restored.players);
        assertEquals(9L, restored.cost);
        assertTrue(context.result().hasFatal());
        assertEquals("__pi_schema", context.result().issues().get(0).path());
    }

    @Test
    void scalarHelpersRoundTripBooleanStringUuidAndResourceLocation() {
        UUID runId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ResourceLocation location = ResourceLocation.parse("test:trial");
        CompoundTag tag = PiSchemaSupport.tagOf(
                PiSchemaSupport.putBoolean("active", true),
                PiSchemaSupport.putString("title", "boss"),
                PiSchemaSupport.putUUID("run_id", runId),
                PiSchemaSupport.putResourceLocation("trial", location)
        );
        PiDecodeContext context = PiDecodeContext.strict();

        assertTrue(PiSchemaSupport.getBoolean(tag, "active", context, false));
        assertEquals("boss", PiSchemaSupport.getString(tag, "title", context, "fallback"));
        assertEquals(runId, PiSchemaSupport.getUUID(tag, "run_id", context, new UUID(0L, 0L)));
        assertEquals(location, PiSchemaSupport.getResourceLocation(tag, "trial", context, ResourceLocation.parse("test:fallback")));
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void scalarHelpersKeepDefaultsAndReportIssues() {
        CompoundTag tag = new CompoundTag();
        tag.putString("active", "yes");
        tag.putString("run_id", "not-a-uuid");
        tag.putString("trial", "bad id");
        PiDecodeContext context = PiDecodeContext.strict();
        UUID fallbackUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        ResourceLocation fallbackLocation = ResourceLocation.parse("test:fallback");

        assertTrue(PiSchemaSupport.getBoolean(tag, "active", context, true));
        assertEquals("fallback", PiSchemaSupport.getString(tag, "title", context, "fallback"));
        assertEquals(fallbackUuid, PiSchemaSupport.getUUID(tag, "run_id", context, fallbackUuid));
        assertEquals(fallbackLocation, PiSchemaSupport.getResourceLocation(tag, "trial", context, fallbackLocation));

        assertEquals(4, context.result().issues().size());
        assertEquals("active", context.result().issues().get(0).path());
        assertEquals("expected boolean tag", context.result().issues().get(0).message());
        assertEquals("title", context.result().issues().get(1).path());
        assertEquals("missing string", context.result().issues().get(1).message());
        assertEquals("run_id", context.result().issues().get(2).path());
        assertEquals("expected uuid tag", context.result().issues().get(2).message());
        assertEquals("trial", context.result().issues().get(3).path());
        assertEquals("invalid resource location", context.result().issues().get(3).message());
        assertFalse(context.result().hasFatal());
    }
}
