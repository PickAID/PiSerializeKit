package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeException;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiDirtySet;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiProjections;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;
import org.pickaid.piserializekit.api.schema.PiStateSnapshot;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.schema.PiSyncSchema;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaField;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaFieldCodecs;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaSerializers;
import org.pickaid.piserializekit.runtime.schema.registry.PiSchemas;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;

class PiSchemaRuntimeTest {
    private static final PiFieldKey PLAYERS = new PiFieldKey(0, "players");
    private static final PiFieldKey COST = new PiFieldKey(1, "cost");
    private static final ResourceLocation SCHEMA_ID = new ResourceLocation("test", "trial_state");
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
    private static final PiFieldKey LEGACY_VALUE = new PiFieldKey(2, "value");
    private static final PiFieldKey LEGACY_LABEL = new PiFieldKey(3, "label");
    private static final ResourceLocation LEGACY_SCHEMA_ID = new ResourceLocation("test", "legacy_state");
    private static final int LEGACY_VERSION = 3;
    private static final PiSchemaField<Integer> LEGACY_VALUE_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(LEGACY_VALUE, PiSyncScope.TRACKING, true),
            serializer(PiSerializers.INT)
    );
    private static final PiSchemaField<String> LEGACY_LABEL_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(LEGACY_LABEL, PiSyncScope.TRACKING, true),
            serializer(PiSerializers.STRING)
    );
    private static final PiFieldKey CHECKPOINTS = new PiFieldKey(4, "checkpoints");
    private static final PiFieldKey MENU_PAGE = new PiFieldKey(5, "menu_page");
    private static final ResourceLocation MANUAL_PERSISTED_SCHEMA_ID = new ResourceLocation("test", "manual_persisted_state");
    private static final int MANUAL_PERSISTED_VERSION = 1;
    private static final PiSchemaField<Set<String>> CHECKPOINTS_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(CHECKPOINTS, PiSyncScope.TRACKING, true),
            PiSerializers.setOf(serializer(PiSerializers.STRING))
    );
    private static final PiSchemaField<Integer> MENU_PAGE_FIELD = new PiSchemaField<>(
            new PiFieldDescriptor(MENU_PAGE, PiSyncScope.MENU, false),
            serializer(PiSerializers.INT)
    );

    private static final class TestState {
        private final List<String> players = new ArrayList<>();
        private long cost;
    }

    private static final class LegacyState {
        private int value;
        private String label = "unset";
    }

    private static final class ManualPersistedState {
        private final Set<String> checkpoints = new LinkedHashSet<>();
        private int menuPage;
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

    private static class LegacyBinding implements PiStateBinding<LegacyState> {
        @Override
        public ResourceLocation schemaId() {
            return LEGACY_SCHEMA_ID;
        }

        @Override
        public int version() {
            return LEGACY_VERSION;
        }

        @Override
        public Class<LegacyState> stateType() {
            return LegacyState.class;
        }

        @Override
        public LegacyState newState() {
            return new LegacyState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(LEGACY_VALUE_FIELD.descriptor(), LEGACY_LABEL_FIELD.descriptor());
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(
                    PiSchemaMigration.step(1, 2, LegacyBinding::upgradeV1ToV2),
                    PiSchemaMigration.step(2, 3, LegacyBinding::upgradeV2ToV3)
            );
        }

        @Override
        public CompoundTag saveFull(LegacyState self) {
            return PiSchemaSupport.tagWithHeader(
                    LEGACY_SCHEMA_ID,
                    LEGACY_VERSION,
                    PiSchemaFieldCodecs.writeField(LEGACY_VALUE_FIELD, self.value),
                    PiSchemaFieldCodecs.writeField(LEGACY_LABEL_FIELD, self.label)
            );
        }

        @Override
        public void loadFull(LegacyState self, CompoundTag tag, PiDecodeContext context) {
            CompoundTag payload = PiSchemaSupport.preparePayload(tag, context, this, PiSchemaPayloadKind.FULL);
            if (payload == null) {
                return;
            }
            self.value = PiSchemaFieldCodecs.readField(payload, LEGACY_VALUE_FIELD, context, self.value);
            self.label = PiSchemaFieldCodecs.readField(payload, LEGACY_LABEL_FIELD, context, self.label);
        }

        @Override
        public CompoundTag saveClientView(LegacyState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(LegacyState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(LEGACY_SCHEMA_ID, LEGACY_VERSION);
            if (dirtySet.contains(LEGACY_VALUE)) {
                tag.put(LEGACY_VALUE_FIELD.key(), PiSchemaFieldCodecs.writeField(LEGACY_VALUE_FIELD, self.value).getSecond());
            }
            if (dirtySet.contains(LEGACY_LABEL)) {
                tag.put(LEGACY_LABEL_FIELD.key(), PiSchemaFieldCodecs.writeField(LEGACY_LABEL_FIELD, self.label).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(LegacyState self, CompoundTag tag, PiDecodeContext context) {
            CompoundTag payload = PiSchemaSupport.preparePayload(tag, context, this, PiSchemaPayloadKind.DELTA);
            if (payload == null) {
                return;
            }
            if (payload.contains(LEGACY_VALUE_FIELD.key())) {
                self.value = PiSchemaFieldCodecs.readField(payload, LEGACY_VALUE_FIELD, context, self.value);
            }
            if (payload.contains(LEGACY_LABEL_FIELD.key())) {
                self.label = PiSchemaFieldCodecs.readField(payload, LEGACY_LABEL_FIELD, context, self.label);
            }
        }

        private static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {
            CompoundTag upgraded = payload.copy();
            if (upgraded.contains("count")) {
                upgraded.put("value", upgraded.get("count"));
                upgraded.remove("count");
            }
            upgraded.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, 2);
            return upgraded;
        }

        private static CompoundTag upgradeV2ToV3(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {
            CompoundTag upgraded = payload.copy();
            if (kind == PiSchemaPayloadKind.FULL && !upgraded.contains("label")) {
                upgraded.putString("label", "legacy");
            }
            upgraded.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, 3);
            return upgraded;
        }
    }

    private static final class ManualPersistedBinding implements PiStateBinding<ManualPersistedState> {
        @Override
        public ResourceLocation schemaId() {
            return MANUAL_PERSISTED_SCHEMA_ID;
        }

        @Override
        public int version() {
            return MANUAL_PERSISTED_VERSION;
        }

        @Override
        public Class<ManualPersistedState> stateType() {
            return ManualPersistedState.class;
        }

        @Override
        public ManualPersistedState newState() {
            return new ManualPersistedState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(CHECKPOINTS_FIELD.descriptor(), MENU_PAGE_FIELD.descriptor());
        }

        @Override
        public CompoundTag saveFull(ManualPersistedState self) {
            return PiSchemaSupport.tagWithHeader(
                    MANUAL_PERSISTED_SCHEMA_ID,
                    MANUAL_PERSISTED_VERSION,
                    PiSchemaFieldCodecs.writeField(CHECKPOINTS_FIELD, self.checkpoints),
                    PiSchemaFieldCodecs.writeField(MENU_PAGE_FIELD, self.menuPage)
            );
        }

        @Override
        public void loadFull(ManualPersistedState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, MANUAL_PERSISTED_SCHEMA_ID, MANUAL_PERSISTED_VERSION)) {
                return;
            }
            self.checkpoints.clear();
            self.checkpoints.addAll(PiSchemaFieldCodecs.readField(tag, CHECKPOINTS_FIELD, context, Set.of()));
            self.menuPage = PiSchemaFieldCodecs.readField(tag, MENU_PAGE_FIELD, context, self.menuPage);
        }

        @Override
        public CompoundTag saveClientView(ManualPersistedState self) {
            return PiSchemaSupport.tagWithHeader(
                    MANUAL_PERSISTED_SCHEMA_ID,
                    MANUAL_PERSISTED_VERSION,
                    PiSchemaFieldCodecs.writeField(CHECKPOINTS_FIELD, self.checkpoints)
            );
        }

        @Override
        public CompoundTag writeDelta(ManualPersistedState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(MANUAL_PERSISTED_SCHEMA_ID, MANUAL_PERSISTED_VERSION);
            if (dirtySet.contains(CHECKPOINTS)) {
                tag.put(CHECKPOINTS_FIELD.key(), PiSchemaFieldCodecs.writeField(CHECKPOINTS_FIELD, self.checkpoints).getSecond());
            }
            if (dirtySet.contains(MENU_PAGE)) {
                tag.put(MENU_PAGE_FIELD.key(), PiSchemaFieldCodecs.writeField(MENU_PAGE_FIELD, self.menuPage).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(ManualPersistedState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, MANUAL_PERSISTED_SCHEMA_ID, MANUAL_PERSISTED_VERSION)) {
                return;
            }
            if (tag.contains(CHECKPOINTS_FIELD.key())) {
                self.checkpoints.addAll(PiSchemaFieldCodecs.readField(tag, CHECKPOINTS_FIELD, context, Set.of()));
            }
            if (tag.contains(MENU_PAGE_FIELD.key())) {
                self.menuPage = PiSchemaFieldCodecs.readField(tag, MENU_PAGE_FIELD, context, self.menuPage);
            }
        }
    }

    private static final class BrokenLegacyBinding extends LegacyBinding {
        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(PiSchemaMigration.step(1, 2, LegacyBinding::upgradeV1ToV2));
        }
    }

    private static final class ThrowingLegacyBinding extends LegacyBinding {
        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(PiSchemaMigration.step(1, 2, ThrowingLegacyBinding::upgradeV1ToV2));
        }

        private static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {
            throw new IllegalStateException();
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
        assertEquals(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, context.result().issues().get(0).code());
        assertEquals("missing field payload", context.result().issues().get(0).message());
        assertFalse(context.result().issues().get(0).fatal());
        assertEquals("cost", context.result().issues().get(1).path());
        assertEquals(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, context.result().issues().get(1).code());
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
        assertEquals(PiDecodeIssueCode.SCHEMA_ID_MISMATCH, context.result().issues().get(0).code());
    }

    @Test
    void scalarHelpersRoundTripBooleanStringUuidAndResourceLocation() {
        UUID runId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ResourceLocation location = new ResourceLocation("test:trial");
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
        assertEquals(location, PiSchemaSupport.getResourceLocation(tag, "trial", context, new ResourceLocation("test:fallback")));
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
        ResourceLocation fallbackLocation = new ResourceLocation("test:fallback");

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
    void serializerBackedFieldHelpersRoundTripScalarTypes() {
        UUID runId = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
        ResourceLocation location = new ResourceLocation("test:trial");
        CompoundTag tag = PiSchemaSupport.tagOf(
                PiSchemaSupport.putField("active", PiSerializers.BOOLEAN, true),
                PiSchemaSupport.putField("title", PiSerializers.STRING, "boss"),
                PiSchemaSupport.putField("run_id", PiSerializers.UUID, runId),
                PiSchemaSupport.putField("trial", PiSerializers.RESOURCE_LOCATION, location)
        );
        PiDecodeContext context = PiDecodeContext.strict();

        assertTrue(PiSchemaSupport.getField(tag, "active", PiSerializers.BOOLEAN, context, false));
        assertEquals("boss", PiSchemaSupport.getField(tag, "title", PiSerializers.STRING, context, "fallback"));
        assertEquals(runId, PiSchemaSupport.getField(tag, "run_id", PiSerializers.UUID, context, new UUID(0L, 0L)));
        assertEquals(
                location,
                PiSchemaSupport.getField(
                        tag,
                        "trial",
                        PiSerializers.RESOURCE_LOCATION,
                        context,
                        new ResourceLocation("test:fallback")
                )
        );
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void serializerBackedFieldHelpersKeepFallbacksOnMissingPayloads() {
        CompoundTag tag = new CompoundTag();
        PiDecodeContext context = PiDecodeContext.strict();
        UUID fallbackUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174011");
        ResourceLocation fallbackLocation = new ResourceLocation("test:fallback");

        assertTrue(PiSchemaSupport.getField(tag, "active", PiSerializers.BOOLEAN, context, true));
        assertEquals("fallback", PiSchemaSupport.getField(tag, "title", PiSerializers.STRING, context, "fallback"));
        assertEquals(fallbackUuid, PiSchemaSupport.getField(tag, "run_id", PiSerializers.UUID, context, fallbackUuid));
        assertEquals(
                fallbackLocation,
                PiSchemaSupport.getField(tag, "trial", PiSerializers.RESOURCE_LOCATION, context, fallbackLocation)
        );
        assertEquals(4, context.result().issues().size());
        assertEquals("active", context.result().issues().get(0).path());
        assertEquals("title", context.result().issues().get(1).path());
        assertEquals("run_id", context.result().issues().get(2).path());
        assertEquals("trial", context.result().issues().get(3).path());
        assertFalse(context.result().hasFatal());
    }

    @Test
    void serializerBackedFieldHelpersSupportLocalCustomSerializerInstances() {
        var trimmed = new TrimmedStringCodec().serializer();
        CompoundTag tag = PiSchemaSupport.tagOf(
                PiSchemaSupport.putField("label", trimmed, "  boss  ")
        );
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("boss", PiSchemaSupport.getField(tag, "label", trimmed, context, "fallback"));
        assertTrue(context.result().issues().isEmpty());
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

    @Test
    void loadFullUpgradesOlderPayloadThroughBindingMigrationChain() {
        LegacyBinding binding = new LegacyBinding();
        LegacyState restored = new LegacyState();
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag legacyPayload = PiSchemaSupport.tagWithHeader(
                LEGACY_SCHEMA_ID,
                1,
                PiSchemaSupport.putInt("count", 5)
        );

        binding.loadFull(restored, legacyPayload, context);

        assertEquals(5, restored.value);
        assertEquals("legacy", restored.label);
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void applyDeltaUpgradesOlderPayloadWithoutInjectingFullDefaults() {
        LegacyBinding binding = new LegacyBinding();
        LegacyState restored = new LegacyState();
        restored.value = 1;
        restored.label = "keep";
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag legacyDelta = PiSchemaSupport.tagWithHeader(
                LEGACY_SCHEMA_ID,
                1,
                PiSchemaSupport.putInt("count", 9)
        );

        binding.applyDelta(restored, legacyDelta, context);

        assertEquals(9, restored.value);
        assertEquals("keep", restored.label);
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void preparePayloadReportsFatalWhenMigrationChainIsIncomplete() {
        BrokenLegacyBinding binding = new BrokenLegacyBinding();
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag legacyPayload = PiSchemaSupport.tagWithHeader(
                LEGACY_SCHEMA_ID,
                1,
                PiSchemaSupport.putInt("count", 7)
        );

        CompoundTag migrated = PiSchemaSupport.preparePayload(legacyPayload, context, binding, PiSchemaPayloadKind.FULL);

        assertNull(migrated);
        assertTrue(context.result().hasFatal());
        assertEquals(PiSchemaSupport.SCHEMA_VERSION_KEY, context.result().issues().get(0).path());
    }

    @Test
    void preparePayloadReportsDeclaredStepsWhenMigrationChainIsIncomplete() {
        BrokenLegacyBinding binding = new BrokenLegacyBinding();
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag legacyPayload = PiSchemaSupport.tagWithHeader(
                LEGACY_SCHEMA_ID,
                1,
                PiSchemaSupport.putInt("count", 7)
        );

        CompoundTag migrated = PiSchemaSupport.preparePayload(legacyPayload, context, binding, PiSchemaPayloadKind.FULL);

        assertNull(migrated);
        assertTrue(context.result().hasFatal());
        assertEquals(
                "missing schema migration path from version 2 to 3; declared steps: 1->2",
                context.result().issues().get(0).message()
        );
    }

    @Test
    void preparePayloadReportsExceptionTypeWhenMigrationThrowsWithoutMessage() {
        ThrowingLegacyBinding binding = new ThrowingLegacyBinding();
        PiDecodeContext context = PiDecodeContext.strict();
        CompoundTag legacyPayload = PiSchemaSupport.tagWithHeader(
                LEGACY_SCHEMA_ID,
                1,
                PiSchemaSupport.putInt("count", 7)
        );

        CompoundTag migrated = PiSchemaSupport.preparePayload(legacyPayload, context, binding, PiSchemaPayloadKind.FULL);

        assertNull(migrated);
        assertTrue(context.result().hasFatal());
        assertEquals(
                "schema migration 1 -> 2 failed: IllegalStateException",
                context.result().issues().get(0).message()
        );
    }

    @Test
    void schemaSerializerThrowsStructuredDecodeException() {
        PiSerializer<TestSchemaProvider.TestState> serializer = PiSchemaSerializers.forState(TestSchemaProvider.TestState.class);
        CompoundTag tag = PiSchemaSupport.headerTag(TestSchemaProvider.SCHEMA_ID, TestSchemaProvider.SCHEMA_VERSION);

        PiDecodeException exception = assertThrows(PiDecodeException.class, () -> serializer.nbtCodec().decode(tag));

        assertEquals(TestSchemaProvider.SCHEMA_ID, exception.schemaId());
        assertEquals(1, exception.result().issues().size());
        assertEquals(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, exception.result().issues().get(0).code());
        assertEquals("value -> missing int", exception.result().summary());
        assertEquals("Failed to decode Pi schema test:schema_provider_state [non-fatal]: value -> missing int", exception.getMessage());
    }

    @Test
    void generatedBindingSaveAndLoadPersistedFieldsIgnoreNonPersistentFields() {
        PiStateBinding<GeneratedProjectionState> binding = PiSchemas.require(GeneratedProjectionState.class);
        GeneratedProjectionState state = new GeneratedProjectionState();
        state.phase = 4;
        state.rewardLabel = "boss";
        state.menuPage = 7;

        CompoundTag persisted = binding.savePersisted(state);

        assertTrue(persisted.contains("phase"));
        assertTrue(persisted.contains("reward_label"));
        assertFalse(persisted.contains("menu_page"));

        GeneratedProjectionState restored = new GeneratedProjectionState();
        restored.menuPage = 99;
        PiDecodeContext context = PiDecodeContext.strict();

        binding.loadPersisted(restored, persisted, context);

        assertEquals(4, restored.phase);
        assertEquals("boss", restored.rewardLabel);
        assertEquals(99, restored.menuPage);
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void defaultLoadPersistedReplaysFullSemanticsForPersistentSubset() {
        PiStateBinding<ManualPersistedState> binding = new ManualPersistedBinding();
        ManualPersistedState source = new ManualPersistedState();
        source.checkpoints.add("fresh");
        source.menuPage = 3;

        CompoundTag persisted = binding.savePersisted(source);

        ManualPersistedState restored = new ManualPersistedState();
        restored.checkpoints.add("stale");
        restored.menuPage = 99;
        PiDecodeContext context = PiDecodeContext.strict();

        binding.loadPersisted(restored, persisted, context);

        assertEquals(Set.of("fresh"), restored.checkpoints);
        assertEquals(99, restored.menuPage);
        assertTrue(context.result().issues().isEmpty());
    }

    @Test
    void defaultPersistedDeltaFiltersNonPersistentFields() {
        PiStateBinding<ManualPersistedState> binding = new ManualPersistedBinding();
        ManualPersistedState state = new ManualPersistedState();
        state.checkpoints.add("fresh");
        state.menuPage = 3;
        PiDirtySet dirty = new PiDirtySet()
                .mark(CHECKPOINTS)
                .mark(MENU_PAGE);

        CompoundTag persistedDelta = binding.writePersistedDelta(state, dirty);

        assertTrue(persistedDelta.contains(PiSchemaSupport.SCHEMA_ID_KEY));
        assertTrue(persistedDelta.contains("checkpoints"));
        assertFalse(persistedDelta.contains("menu_page"));
    }

    @Test
    void generatedBindingSupportsProjectionFilteredDeltaAndSnapshots() {
        PiStateBinding<GeneratedProjectionState> binding = PiSchemas.require(GeneratedProjectionState.class);
        GeneratedProjectionState state = new GeneratedProjectionState();
        state.phase = 1;
        state.rewardLabel = "iron";
        state.menuPage = 2;

        PiStateSnapshot baseline = binding.snapshot(state);
        state.phase = 3;
        state.menuPage = 5;

        CompoundTag clientDelta = binding.writeDelta(
                state,
                binding.diff(state, baseline),
                PiProjections.client()
        );

        assertTrue(clientDelta.contains("phase"));
        assertFalse(clientDelta.contains("reward_label"));
        assertFalse(clientDelta.contains("menu_page"));
    }

    @Test
    void generatedNestedDeltaModeAppliesIntoExistingNestedStateInstance() {
        PiStateBinding<GeneratedNestedDeltaState> binding = PiSchemas.require(GeneratedNestedDeltaState.class);
        GeneratedNestedDeltaState state = new GeneratedNestedDeltaState();
        state.child.value = 8;

        GeneratedNestedDeltaState restored = new GeneratedNestedDeltaState();
        GeneratedChildState originalChild = restored.child;
        PiDirtySet dirty = new PiDirtySet().mark(fieldKey(binding, "child"));

        binding.applyDelta(restored, binding.writeDelta(state, dirty), PiDecodeContext.strict());

        assertTrue(originalChild == restored.child);
        assertEquals(8, restored.child.value);
    }

    @Test
    void generatedMergeDeltaModesPreserveExistingSetAndMapEntriesDuringDeltaApply() {
        PiStateBinding<GeneratedMergeState> binding = PiSchemas.require(GeneratedMergeState.class);
        GeneratedMergeState state = new GeneratedMergeState();
        state.checkpoints.add(new ResourceLocation("test", "new"));
        state.weights.put("new", 2);

        GeneratedMergeState restored = new GeneratedMergeState();
        restored.checkpoints.add(new ResourceLocation("test", "keep"));
        restored.weights.put("keep", 1);

        PiDirtySet dirty = new PiDirtySet()
                .mark(fieldKey(binding, "checkpoints"))
                .mark(fieldKey(binding, "weights"));

        binding.applyDelta(restored, binding.writeDelta(state, dirty), PiDecodeContext.strict());

        assertEquals(
                List.of(
                        new ResourceLocation("test", "keep"),
                        new ResourceLocation("test", "new")
                ),
                List.copyOf(restored.checkpoints)
        );
        assertEquals(Map.of("keep", 1, "new", 2), restored.weights);
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
