package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDirtySet;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.schema.PiSyncSchema;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;

class PiSchemaRuntimeTest {
    private static final PiFieldKey PLAYERS = new PiFieldKey(0, "players");
    private static final PiFieldKey COST = new PiFieldKey(1, "cost");
    private static final ResourceLocation SCHEMA_ID = ResourceLocation.fromNamespaceAndPath("test", "trial_state");
    private static final int VERSION = 3;
    private static final PiSerializeRuntime RUNTIME = createRuntime();
    private static final PiSchemaField<List<String>> PLAYERS_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(PLAYERS, PiSyncScope.TRACKING, true),
            PiSerializers.listOf(serializer(PiSerializers.STRING))
    );
    private static final PiSchemaField<Long> COST_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(COST, PiSyncScope.OWNER, true),
            serializer(PiSerializers.LONG)
    );

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
                    PiSchemaFieldCodecs.writeField(PLAYERS_FIELD, self.players),
                    PiSchemaFieldCodecs.writeField(COST_FIELD, self.cost)
            );
        }

        @Override
        public void loadFull(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {
                return;
            }
            self.players.clear();
            self.players.addAll(PiSchemaFieldCodecs.readField(tag, PLAYERS_FIELD, context, List.of()));
            self.cost = PiSchemaFieldCodecs.readField(tag, COST_FIELD, context, 0L);
        }

        @Override
        public CompoundTag saveClientView(TestState self) {
            return PiSchemaSupport.tagWithHeader(
                    SCHEMA_ID,
                    VERSION,
                    PiSchemaFieldCodecs.writeField(PLAYERS_FIELD, self.players)
            );
        }

        @Override
        public CompoundTag writeDelta(TestState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);
            if (dirtySet.contains(PLAYERS)) {
                tag.put(PLAYERS_FIELD.key(), PiSchemaFieldCodecs.writeField(PLAYERS_FIELD, self.players).getSecond());
            }
            if (dirtySet.contains(COST)) {
                tag.put(COST_FIELD.key(), PiSchemaFieldCodecs.writeField(COST_FIELD, self.cost).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {
                return;
            }
            if (tag.contains(PLAYERS_FIELD.key())) {
                self.players.clear();
                self.players.addAll(PiSchemaFieldCodecs.readField(tag, PLAYERS_FIELD, context, List.of()));
            }
            if (tag.contains(COST_FIELD.key())) {
                self.cost = PiSchemaFieldCodecs.readField(tag, COST_FIELD, context, self.cost);
            }
        }
    }

    private static PiSerializeRuntime createRuntime() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);
        return runtime;
    }

    private static <T> PiSerializer<T> serializer(org.pickaid.piserializekit.api.service.PiSerializerType<T> type) {
        return RUNTIME.lookup(type).orElseThrow();
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

        assertTrue(client.contains(PLAYERS_FIELD.key()));
        assertFalse(client.contains(COST_FIELD.key()));
        assertEquals(SCHEMA_ID.toString(), client.getString("__pi_schema"));
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
        assertEquals(SCHEMA_ID.toString(), playersDelta.getString("__pi_schema"));
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
        assertEquals("missing field payload", context.result().issues().get(0).message());
        assertFalse(context.result().issues().get(0).fatal());
        assertEquals("cost", context.result().issues().get(1).path());
        assertEquals("missing field payload", context.result().issues().get(1).message());
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
        tag.putString("__pi_schema", "other:state");

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

    @Test
    void generatedSchemaRoundTripsNestedCollectionsAndCustomFieldThroughFullAndDelta() {
        GeneratedComplexState state = new GeneratedComplexState();
        state.names.add("alice");
        state.counts.put("iron", 3);
        state.child.value = 9;
        state.label = "  boss  ";
        PiStateBinding<GeneratedComplexState> binding = PiSchemas.require(GeneratedComplexState.class);

        GeneratedComplexState fullRestored = staleGeneratedComplexState();
        PiDecodeContext fullContext = PiDecodeContext.strict();

        binding.loadFull(fullRestored, binding.saveFull(state), fullContext);

        assertEquals(List.of("alice"), fullRestored.names);
        assertEquals(Map.of("iron", 3), fullRestored.counts);
        assertEquals(9, fullRestored.child.value);
        assertEquals("boss", fullRestored.label);
        assertTrue(fullContext.result().issues().isEmpty());

        GeneratedComplexState deltaRestored = staleGeneratedComplexState();
        PiDecodeContext deltaContext = PiDecodeContext.strict();
        PiDirtySet dirtySet = new PiDirtySet()
                .mark(fieldKey(binding, "names"))
                .mark(fieldKey(binding, "counts"))
                .mark(fieldKey(binding, "child"))
                .mark(fieldKey(binding, "label"));

        binding.applyDelta(
                deltaRestored,
                binding.writeDelta(state, dirtySet),
                deltaContext
        );

        assertEquals(List.of("alice"), deltaRestored.names);
        assertEquals(Map.of("iron", 3), deltaRestored.counts);
        assertEquals(9, deltaRestored.child.value);
        assertEquals("boss", deltaRestored.label);
        assertTrue(deltaContext.result().issues().isEmpty());
    }

    @Test
    void generatedSchemaKeepsExistingFinalCollectionValuesWhenDeltaDecodeFails() {
        GeneratedComplexState restored = staleGeneratedComplexState();
        PiStateBinding<GeneratedComplexState> binding = PiSchemas.require(GeneratedComplexState.class);
        CompoundTag delta = PiSchemaSupport.headerTag(binding.schemaId(), binding.version());
        delta.putString("names", "broken");
        delta.putString("counts", "broken");
        PiDecodeContext context = PiDecodeContext.strict();

        binding.applyDelta(restored, delta, context);

        assertEquals(List.of("stale"), restored.names);
        assertEquals(Map.of("old", 1), restored.counts);
        assertEquals(2, context.result().issues().size());
        assertEquals("names", context.result().issues().get(0).path());
        assertEquals("counts", context.result().issues().get(1).path());
    }

    private static GeneratedComplexState staleGeneratedComplexState() {
        GeneratedComplexState state = new GeneratedComplexState();
        state.names.add("stale");
        state.counts.put("old", 1);
        state.child.value = 1;
        state.label = "stale";
        return state;
    }

    private static PiFieldKey fieldKey(PiStateBinding<?> binding, String id) {
        return binding.fields().stream()
                .map(PiFieldDescriptor::key)
                .filter(key -> key.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field key for " + id));
    }
}
