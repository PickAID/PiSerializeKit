package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class PiSyncModelProcessorTest {
    @Test
    void rejectsStateWithInvalidSchemaId() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidSchemaState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:bad path\", version = 1)",
                "public final class InvalidSchemaState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSyncModel.id must be a namespace:path resource location");
    }

    @Test
    void rejectsStateWithInvalidSchemaIdAndReportsActualValue() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidSchemaState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:bad path\", version = 1)",
                "public final class InvalidSchemaState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSyncModel.id must be a namespace:path resource location: example:bad path"
        );
    }

    @Test
    void rejectsDuplicateSchemaIdsAcrossSyncModels() {
        JavaFileObject first = JavaFileObjects.forSourceLines(
                "example.FirstState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:shared_state\", version = 1)",
                "public final class FirstState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );
        JavaFileObject second = JavaFileObjects.forSourceLines(
                "example.SecondState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:shared_state\", version = 1)",
                "public final class SecondState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(first, second);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Duplicate Pi schema id example:shared_state");
    }

    @Test
    void rejectsStateWithNonPositiveSchemaVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidVersionState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_version_state\", version = 0)",
                "public final class InvalidVersionState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSyncModel.version must be >= 1");
    }

    @Test
    void rejectsNestedSyncModelTypesBeforeCodeGeneration() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.OuterStateHolder",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "public final class OuterStateHolder {",
                "  @PiSyncModel(id = \"example:nested_state\", version = 1)",
                "  public static final class NestedState {",
                "    @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "    public int count;",
                "    public NestedState() {",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSyncModel types must be top-level classes because generated companions are emitted as package-level types"
        );
    }

    @Test
    void rejectsCompactPiFieldAuthoringOnSyncModels() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CompactState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "@PiSyncModel(id = \"example:compact_state\", version = 1)",
                "public final class CompactState {",
                "  @PiField",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField on @PiSyncModel types must explicitly declare id, sync, and persist"
        );
    }

    @Test
    void rejectsBlankDeclaredFieldIdsOnSyncModels() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.BlankIdState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:blank_id_state\", version = 1)",
                "public final class BlankIdState {",
                "  @PiField(id = \"\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id on @PiSyncModel field count must be non-blank"
        );
    }

    @Test
    void rejectsPrivateSyncModelFieldsBeforeGeneratedCodeCompilation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.PrivateState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:private_state\", version = 1)",
                "public final class PrivateState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  private int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField field count must not be private because generated bindings access fields directly"
        );
    }

    @Test
    void rejectsStaticSyncModelFieldsInsteadOfIgnoringThem() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StaticState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:static_state\", version = 1)",
                "public final class StaticState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public static int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField field count must not be static because generated bindings only support instance state"
        );
    }

    @Test
    void rejectsPiFieldOutsideSyncModelOrPacketHost() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StrayFieldHolder",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "public final class StrayFieldHolder {",
                "  @PiField",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField may only be declared inside @PiSyncModel or @PiPacket types"
        );
    }

    @Test
    void rejectsPiAfterDecodeOutsideSyncModelHost() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StrayAfterDecodeHolder",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiAfterDecode;",
                "public final class StrayAfterDecodeHolder {",
                "  @PiAfterDecode",
                "  void afterDecode() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiAfterDecode may only be declared inside @PiSyncModel types"
        );
    }

    @Test
    void rejectsPiSchemaUpgradeOutsideSyncModelHost() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StraySchemaUpgradeHolder",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "public final class StraySchemaUpgradeHolder {",
                "  @PiSchemaUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgrade(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade may only be declared inside @PiSyncModel types"
        );
    }

    @Test
    void rejectsPiPacketUpgradeOutsidePacketHost() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StrayPacketUpgradeHolder",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "public final class StrayPacketUpgradeHolder {",
                "  @PiPacketUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgrade(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacketUpgrade may only be declared inside @PiPacket types"
        );
    }

    @Test
    void rejectsTransientSyncModelFieldsBecausePiFieldAlreadyOwnsPersistenceSemantics() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.TransientState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:transient_state\", version = 1)",
                "public final class TransientState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public transient int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField field count must not be transient because PiSerializeKit already controls persistence and transport semantics"
        );
    }

    @Test
    void generatesSchemaAndFieldConstants() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.TrialState",
                "package example;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:trial_state\", version = 2)",
                "public final class TrialState {",
                "  @PiField(id = \"players\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public final List<String> players = new ArrayList<>();",
                "  @PiField(id = \"energy\", sync = PiSyncScope.OWNER, persist = true)",
                "  public int energy;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.TrialState_PiSchema");
        assertThat(compilation).generatedSourceFile("example.TrialState_PiFields");
        assertThat(compilation).generatedSourceFile("example.TrialState_PiSchemaProvider");

        assertGeneratedContains(compilation, "example.TrialState_PiFields", "public static final PiFieldKey PLAYERS = new PiFieldKey(0, \"players\");");
        assertGeneratedContains(compilation, "example.TrialState_PiFields", "public static final PiFieldKey ENERGY = new PiFieldKey(1, \"energy\");");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final int VERSION = 2;");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final int FIELD_COUNT = 2;");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final String SCHEMA_ID = \"example:trial_state\";");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public ResourceLocation schemaId()");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return new ResourceLocation(\"example\", \"trial_state\");");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public int version()");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return VERSION;");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiStateBinding<TrialState> BINDING = new PiStateBinding<>() {");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public Class<TrialState> stateType()");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return TrialState.class;");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public TrialState newState()");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return new TrialState();");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public List<PiFieldDescriptor> fields()");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return FIELDS;");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiFieldDescriptor PLAYERS = new PiFieldDescriptor(TrialState_PiFields.PLAYERS, PiSyncScope.TRACKING, true);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiFieldDescriptor ENERGY = new PiFieldDescriptor(TrialState_PiFields.ENERGY, PiSyncScope.OWNER, true);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final List<PiFieldDescriptor> FIELDS = List.of(PLAYERS, ENERGY);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag saveFull(TrialState self)");
        assertMethodContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag saveFull(TrialState self)", "public static CompoundTag saveClientView(TrialState self)", "CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);");
        assertMethodContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag saveFull(TrialState self)", "public static CompoundTag saveClientView(TrialState self)", "PiSchemaFieldCodecs.writeField(tag, PLAYERS_FIELD, self.players);");
        assertMethodContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag saveFull(TrialState self)", "public static CompoundTag saveClientView(TrialState self)", "PiSchemaFieldCodecs.writeField(tag, ENERGY_FIELD, self.energy);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static void loadFull(TrialState self, CompoundTag tag, PiDecodeContext context)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.FULL);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag writeDelta(TrialState self, PiDirtySet dirtySet)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static void applyDelta(TrialState self, CompoundTag tag, PiDecodeContext context)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiSchemaField<java.util.List<java.lang.String>> PLAYERS_FIELD = new PiSchemaField<>(PLAYERS, PiSerializers.listOf(requireSerializer(PiSerializers.STRING)));");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiSchemaField<java.lang.Integer> ENERGY_FIELD = new PiSchemaField<>(ENERGY, requireSerializer(PiSerializers.INT));");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final List<PiSchemaField<?>> SCHEMA_FIELDS = List.of(PLAYERS_FIELD, ENERGY_FIELD);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaFieldCodecs.encodeField(PLAYERS_FIELD, self.players)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaFieldCodecs.encodeField(ENERGY_FIELD, self.energy)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static PiStateSnapshot snapshot(TrialState self)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "new net.minecraft.nbt.Tag[]{");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static PiDirtyBits diff(TrialState self, PiStateSnapshot snapshot)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaFieldCodecs.readFieldInto(__pi_payload, PLAYERS_FIELD, context, self.players);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "self.energy = PiSchemaFieldCodecs.readFieldInto(__pi_payload, ENERGY_FIELD, context, self.energy);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "dirtySet.contains(TrialState_PiFields.PLAYERS)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "dirtySet.contains(TrialState_PiFields.ENERGY)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public PiStateSnapshot snapshot(TrialState self)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return TrialState_PiSchema.snapshot(self);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public PiDirtyBits diff(TrialState self, PiStateSnapshot snapshot)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return TrialState_PiSchema.diff(self, snapshot);");
    }

    @Test
    void generatesSchemaForBooleanStringUuidAndResourceLocationFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.AdvancedState",
                "package example;",
                "import java.util.UUID;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:advanced_state\", version = 3)",
                "public final class AdvancedState {",
                "  @PiField(id = \"active\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public boolean active = true;",
                "  @PiField(id = \"owner_name\", sync = PiSyncScope.OWNER, persist = true)",
                "  public String ownerName = \"fallback\";",
                "  @PiField(id = \"run_id\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public UUID runId = new UUID(1L, 2L);",
                "  @PiField(id = \"trial\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public ResourceLocation trial = new ResourceLocation(\"example:trial\");",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.AdvancedState_PiSchema");
        assertThat(compilation).generatedSourceFile("example.AdvancedState_PiSchemaProvider");

        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "public static final PiSchemaField<java.lang.Boolean> ACTIVE_FIELD = new PiSchemaField<>(ACTIVE, requireSerializer(PiSerializers.BOOLEAN));");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "public static final PiSchemaField<java.lang.String> OWNER_NAME_FIELD = new PiSchemaField<>(OWNER_NAME, requireSerializer(PiSerializers.STRING));");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "public static final PiSchemaField<java.util.UUID> RUN_ID_FIELD = new PiSchemaField<>(RUN_ID, requireSerializer(PiSerializers.UUID));");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "public static final PiSchemaField<net.minecraft.resources.ResourceLocation> TRIAL_FIELD = new PiSchemaField<>(TRIAL, requireSerializer(PiSerializers.RESOURCE_LOCATION));");
        assertMethodContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);");
        assertMethodContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, ACTIVE_FIELD, self.active);");
        assertMethodContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, OWNER_NAME_FIELD, self.ownerName);");
        assertMethodContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, RUN_ID_FIELD, self.runId);");
        assertMethodContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, TRIAL_FIELD, self.trial);");
        assertMethodNotContains(compilation, "example.AdvancedState_PiSchema", "public static CompoundTag saveFull(AdvancedState self)", "public static CompoundTag saveClientView", "return PiSchemaSupport.tagWithHeader(");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.active = PiSchemaFieldCodecs.readFieldInto(__pi_payload, ACTIVE_FIELD, context, self.active);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.ownerName = PiSchemaFieldCodecs.readFieldInto(__pi_payload, OWNER_NAME_FIELD, context, self.ownerName);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.runId = PiSchemaFieldCodecs.readFieldInto(__pi_payload, RUN_ID_FIELD, context, self.runId);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.trial = PiSchemaFieldCodecs.readFieldInto(__pi_payload, TRIAL_FIELD, context, self.trial);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchemaProvider", "public final class AdvancedState_PiSchemaProvider implements PiSchemaProvider");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchemaProvider", "registry.register(AdvancedState.class, AdvancedState_PiSchema.BINDING);");
    }

    @Test
    void generatesSchemaForByteShortFloatAndDoubleFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.NumericState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:numeric_state\", version = 1)",
                "public final class NumericState {",
                "  @PiField(id = \"heat\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public byte heat = 7;",
                "  @PiField(id = \"tier\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public Short tier = 2;",
                "  @PiField(id = \"ratio\", sync = PiSyncScope.GLOBAL, persist = true)",
                "  public float ratio = 1.25F;",
                "  @PiField(id = \"precision\", sync = PiSyncScope.OWNER, persist = true)",
                "  public Double precision = 3.5D;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "public static final PiSchemaField<java.lang.Byte> HEAT_FIELD = new PiSchemaField<>(HEAT, requireSerializer(PiSerializers.BYTE));");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "public static final PiSchemaField<java.lang.Short> TIER_FIELD = new PiSchemaField<>(TIER, requireSerializer(PiSerializers.SHORT));");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "public static final PiSchemaField<java.lang.Float> RATIO_FIELD = new PiSchemaField<>(RATIO, requireSerializer(PiSerializers.FLOAT));");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "public static final PiSchemaField<java.lang.Double> PRECISION_FIELD = new PiSchemaField<>(PRECISION, requireSerializer(PiSerializers.DOUBLE));");
        assertMethodContains(compilation, "example.NumericState_PiSchema", "public static CompoundTag saveFull(NumericState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, HEAT_FIELD, self.heat);");
        assertMethodContains(compilation, "example.NumericState_PiSchema", "public static CompoundTag saveFull(NumericState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, TIER_FIELD, self.tier);");
        assertMethodContains(compilation, "example.NumericState_PiSchema", "public static CompoundTag saveFull(NumericState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, RATIO_FIELD, self.ratio);");
        assertMethodContains(compilation, "example.NumericState_PiSchema", "public static CompoundTag saveFull(NumericState self)", "public static CompoundTag saveClientView", "PiSchemaFieldCodecs.writeField(tag, PRECISION_FIELD, self.precision);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.heat = PiSchemaFieldCodecs.readFieldInto(__pi_payload, HEAT_FIELD, context, self.heat);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.tier = PiSchemaFieldCodecs.readFieldInto(__pi_payload, TIER_FIELD, context, self.tier);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.ratio = PiSchemaFieldCodecs.readFieldInto(__pi_payload, RATIO_FIELD, context, self.ratio);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.precision = PiSchemaFieldCodecs.readFieldInto(__pi_payload, PRECISION_FIELD, context, self.precision);");
    }

    @Test
    void generatesDecodeHookInvocation() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.HookedState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiAfterDecode;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:hooked_state\", version = 1)",
                "public final class HookedState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  @PiAfterDecode",
                "  void normalize() {",
                "    if (count < 0) count = 0;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.HookedState_PiSchema", "self.normalize();");
    }

    @Test
    void rejectsInvalidAfterDecodeMethodShape() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidHookedState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiAfterDecode;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_hooked_state\", version = 1)",
                "public final class InvalidHookedState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  @PiAfterDecode",
                "  private int normalize(int ignored) {",
                "    return count;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiAfterDecode methods must be non-static, non-private, no-arg, and return void"
        );
    }

    @Test
    void rejectsAfterDecodeMethodThatThrowsCheckedException() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedHookedState",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.piserializekit.api.schema.PiAfterDecode;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:checked_hooked_state\", version = 1)",
                "public final class CheckedHookedState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  @PiAfterDecode",
                "  void normalize() throws IOException {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiAfterDecode methods must not throw checked exceptions because generated bindings invoke them directly"
        );
    }

    @Test
    void rejectsMultipleAfterDecodeMethodsOnOneState() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.DuplicateHooksState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiAfterDecode;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:duplicate_hooks_state\", version = 1)",
                "public final class DuplicateHooksState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  @PiAfterDecode",
                "  void normalizeFirst() {",
                "  }",
                "  @PiAfterDecode",
                "  void normalizeSecond() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiAfterDecode allows only one method per @PiSyncModel type"
        );
    }

    @Test
    void generatesMigrationChainAndPayloadPreparationCalls() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacyState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:legacy_state\", version = 3)",
                "public final class LegacyState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "  @PiSchemaUpgrade(from = 2, to = 3)",
                "  static CompoundTag upgradeV2ToV3(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "public static final List<PiSchemaMigration> MIGRATIONS = List.of(");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "PiSchemaMigration.step(1, 2, LegacyState::upgradeV1ToV2)");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "PiSchemaMigration.step(2, 3, LegacyState::upgradeV2ToV3)");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "public List<PiSchemaMigration> migrations()");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "return MIGRATIONS;");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.FULL);");
        assertGeneratedContains(compilation, "example.LegacyState_PiSchema", "CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.DELTA);");
    }

    @Test
    void rejectsSchemaMigrationChainsThatLeaveOlderVersionsUnreachable() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.GappedLegacyState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:gapped_legacy_state\", version = 4)",
                "public final class GappedLegacyState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 3)",
                "  static CompoundTag upgradeV1ToV3(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "  @PiSchemaUpgrade(from = 3, to = 4)",
                "  static CompoundTag upgradeV3ToV4(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSchemaUpgrade chain must define a migration path from version 2 to 4");
    }

    @Test
    void rejectsSchemaMigrationsThatOvershootDeclaredSchemaVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.OvershootLegacyState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:overshoot_legacy_state\", version = 3)",
                "public final class OvershootLegacyState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 4)",
                "  static CompoundTag upgradeV1ToV4(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade step upgradeV1ToV4 declares to=4 above declared schema version 3"
        );
    }

    @Test
    void rejectsInvalidSchemaMigrationMethodShape() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidSchemaMigrationState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_schema_migration_state\", version = 2)",
                "public final class InvalidSchemaMigrationState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 2)",
                "  CompoundTag upgradeV1ToV2(CompoundTag payload, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade methods must match: static CompoundTag method(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context); found: CompoundTag upgradeV1ToV2(CompoundTag payload, PiDecodeContext context)"
        );
    }

    @Test
    void rejectsSchemaMigrationMethodThatThrowsCheckedException() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedSchemaMigrationState",
                "package example;",
                "import java.io.IOException;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:checked_schema_migration_state\", version = 2)",
                "public final class CheckedSchemaMigrationState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) throws IOException {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade methods must not throw checked exceptions because generated migrations invoke them directly"
        );
    }

    @Test
    void rejectsConflictingSchemaMigrationMethodsForSameSourceVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.ConflictingSchemaMigrationState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:conflicting_schema_migration_state\", version = 3)",
                "public final class ConflictingSchemaMigrationState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "  @PiSchemaUpgrade(from = 1, to = 3)",
                "  static CompoundTag upgradeV1ToV3(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade allows only one migration step from version 1; existing method: upgradeV1ToV2, conflicting method: upgradeV1ToV3"
        );
    }

    @Test
    void rejectsSchemaMigrationSourcesAtOrAboveDeclaredSchemaVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LateSchemaMigrationState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:late_schema_migration_state\", version = 3)",
                "public final class LateSchemaMigrationState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 3, to = 4)",
                "  static CompoundTag upgradeV3ToV4(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSchemaUpgrade step upgradeV3ToV4 declares from=3 at or above declared schema version 3"
        );
    }

    @Test
    void rejectsSchemaMigrationStepsWithInvalidBounds() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidSchemaMigrationBoundsState",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_schema_migration_bounds_state\", version = 2)",
                "public final class InvalidSchemaMigrationBoundsState {",
                "  @PiField(id = \"value\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int value;",
                "  @PiSchemaUpgrade(from = 0, to = 0)",
                "  static CompoundTag upgradeV0ToV0(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSchemaUpgrade requires to > from >= 1");
    }

    @Test
    void generatesSerializerBackedFieldsForNestedOptionalCollectionsAndCustomCodec() throws Exception {
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.ComplexState",
                "package example;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import java.util.Optional;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.world.item.ItemStack;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:complex_state\", version = 2)",
                "public final class ComplexState {",
                "  @PiField(id = \"targets\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public final List<BlockPos> targets = new ArrayList<>();",
                "  @PiField(id = \"reward\", sync = PiSyncScope.OWNER, persist = true, serializer = GhostSlotCodec.class)",
                "  public ItemStack reward = ItemStack.EMPTY;",
                "  @PiField(id = \"anchor\", sync = PiSyncScope.OWNER, persist = true)",
                "  public Optional<BlockPos> anchor = Optional.empty();",
                "}"
        );
        JavaFileObject codec = JavaFileObjects.forSourceLines(
                "example.GhostSlotCodec",
                "package example;",
                "import net.minecraft.world.item.ItemStack;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "public final class GhostSlotCodec implements PiFieldCodecProvider<ItemStack> {",
                "  @Override",
                "  public PiSerializer<ItemStack> serializer() {",
                "    return null;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(state, codec);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "public static final PiSchemaField<java.util.List<net.minecraft.core.BlockPos>> TARGETS_FIELD = new PiSchemaField<>(TARGETS, PiSerializers.listOf(requireSerializer(PiSerializers.BLOCK_POS)));");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "public static final PiSchemaField<net.minecraft.world.item.ItemStack> REWARD_FIELD = new PiSchemaField<>(REWARD, new example.GhostSlotCodec().serializer());");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "public static final PiSchemaField<java.util.Optional<net.minecraft.core.BlockPos>> ANCHOR_FIELD = new PiSchemaField<>(ANCHOR, PiSerializers.optionalOf(requireSerializer(PiSerializers.BLOCK_POS)));");
        assertMethodContains(compilation, "example.ComplexState_PiSchema", "public static CompoundTag saveFull(ComplexState self)", "public static CompoundTag saveClientView(ComplexState self)", "PiSchemaFieldCodecs.writeField(tag, TARGETS_FIELD, self.targets);");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "PiSchemaFieldCodecs.readFieldInto(__pi_payload, TARGETS_FIELD, context, self.targets);");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "self.reward = PiSchemaFieldCodecs.readFieldInto(__pi_payload, REWARD_FIELD, context, self.reward);");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "self.anchor = PiSchemaFieldCodecs.readFieldInto(__pi_payload, ANCHOR_FIELD, context, self.anchor);");
    }

    @Test
    void generatesRecursiveSerializerExpressionsForEnumSetAndMapFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.GraphState",
                "package example;",
                "import java.util.LinkedHashMap;",
                "import java.util.LinkedHashSet;",
                "import java.util.Map;",
                "import java.util.Set;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:graph_state\", version = 1)",
                "public final class GraphState {",
                "  @PiField(id = \"mode\", sync = PiSyncScope.OWNER, persist = true)",
                "  public Mode mode = Mode.IDLE;",
                "  @PiField(id = \"checkpoints\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public final Set<ResourceLocation> checkpoints = new LinkedHashSet<>();",
                "  @PiField(id = \"weights\", sync = PiSyncScope.OWNER, persist = true)",
                "  public final Map<String, Integer> weights = new LinkedHashMap<>();",
                "  public enum Mode {",
                "    IDLE, ACTIVE",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "public static final PiSchemaField<example.GraphState.Mode> MODE_FIELD = new PiSchemaField<>(MODE, PiSerializers.enumType(example.GraphState.Mode.class));");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "public static final PiSchemaField<java.util.Set<net.minecraft.resources.ResourceLocation>> CHECKPOINTS_FIELD = new PiSchemaField<>(CHECKPOINTS, PiSerializers.setOf(requireSerializer(PiSerializers.RESOURCE_LOCATION)));");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "public static final PiSchemaField<java.util.Map<java.lang.String, java.lang.Integer>> WEIGHTS_FIELD = new PiSchemaField<>(WEIGHTS, PiSerializers.mapOf(requireSerializer(PiSerializers.STRING), requireSerializer(PiSerializers.INT)));");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "PiSchemaFieldCodecs.readFieldInto(__pi_payload, CHECKPOINTS_FIELD, context, self.checkpoints);");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "PiSchemaFieldCodecs.readFieldInto(__pi_payload, WEIGHTS_FIELD, context, self.weights);");
    }

    @Test
    void clientViewEmitsOnlyPublicClientScopes() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.VisibilityState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:visibility_state\", version = 1)",
                "public final class VisibilityState {",
                "  @PiField(id = \"chunk\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int chunk;",
                "  @PiField(id = \"tracking\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int tracking;",
                "  @PiField(id = \"global\", sync = PiSyncScope.GLOBAL, persist = true)",
                "  public int global;",
                "  @PiField(id = \"owner\", sync = PiSyncScope.OWNER, persist = true)",
                "  public int owner;",
                "  @PiField(id = \"menu\", sync = PiSyncScope.MENU, persist = true)",
                "  public int menu;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertMethodContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static CompoundTag savePersisted(VisibilityState self)",
                "PiSchemaFieldCodecs.writeField(tag, CHUNK_FIELD, self.chunk);"
        );
        assertMethodContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static CompoundTag savePersisted(VisibilityState self)",
                "PiSchemaFieldCodecs.writeField(tag, TRACKING_FIELD, self.tracking);"
        );
        assertMethodContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static CompoundTag savePersisted(VisibilityState self)",
                "PiSchemaFieldCodecs.writeField(tag, GLOBAL_FIELD, self.global);"
        );
        assertMethodNotContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static CompoundTag savePersisted(VisibilityState self)",
                "PiSchemaFieldCodecs.writeField(tag, OWNER_FIELD, self.owner);"
        );
        assertMethodNotContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static CompoundTag savePersisted(VisibilityState self)",
                "PiSchemaFieldCodecs.writeField(tag, MENU_FIELD, self.menu);"
        );
    }

    @Test
    void persistedViewEmitsOnlyPersistentFields() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.PersistenceState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:persistence_state\", version = 1)",
                "public final class PersistenceState {",
                "  @PiField(id = \"phase\", sync = PiSyncScope.GLOBAL, persist = true)",
                "  public int phase;",
                "  @PiField(id = \"reward\", sync = PiSyncScope.OWNER, persist = true)",
                "  public String reward = \"fallback\";",
                "  @PiField(id = \"menu\", sync = PiSyncScope.MENU, persist = false)",
                "  public int menu;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertMethodContains(
                compilation,
                "example.PersistenceState_PiSchema",
                "public static CompoundTag savePersisted(PersistenceState self)",
                "public static PiStateSnapshot snapshot(PersistenceState self)",
                "PiSchemaFieldCodecs.writeField(tag, PHASE_FIELD, self.phase);"
        );
        assertMethodContains(
                compilation,
                "example.PersistenceState_PiSchema",
                "public static CompoundTag savePersisted(PersistenceState self)",
                "public static PiStateSnapshot snapshot(PersistenceState self)",
                "PiSchemaFieldCodecs.writeField(tag, REWARD_FIELD, self.reward);"
        );
        assertMethodNotContains(
                compilation,
                "example.PersistenceState_PiSchema",
                "public static CompoundTag savePersisted(PersistenceState self)",
                "public static PiStateSnapshot snapshot(PersistenceState self)",
                "PiSchemaFieldCodecs.writeField(tag, MENU_FIELD, self.menu);"
        );
        assertGeneratedContains(compilation, "example.PersistenceState_PiSchema", "CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.PERSISTED);");
    }

    @Test
    void rejectsDuplicatePiFieldIdsWithinOneState() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.DuplicateFieldIdState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:duplicate_field_id_state\", version = 1)",
                "public final class DuplicateFieldIdState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  @PiField(id = \"count\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public int mirroredCount;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Duplicate @PiField.id \"count\" in DuplicateFieldIdState");
    }

    @Test
    void rejectsSchemaFieldIdsThatAreNotStablePayloadKeys() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidFieldKeyState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_field_key_state\", version = 1)",
                "public final class InvalidFieldKeyState {",
                "  @PiField(id = \"Count Value\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id for field count must resolve to a valid payload key"
        );
    }

    @Test
    void rejectsSchemaFieldIdsUsingReservedFrameworkPrefix() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.ReservedFieldKeyState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:reserved_field_key_state\", version = 1)",
                "public final class ReservedFieldKeyState {",
                "  @PiField(id = \"__pi_schema\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id for field count uses reserved Pi payload prefix __pi_"
        );
    }

    @Test
    void generatesSchemaBackedSerializerForNestedSyncModels() throws Exception {
        JavaFileObject child = JavaFileObjects.forSourceLines(
                "example.ChildState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:child_state\", version = 1)",
                "public final class ChildState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );
        JavaFileObject parent = JavaFileObjects.forSourceLines(
                "example.ParentState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:parent_state\", version = 1)",
                "public final class ParentState {",
                "  @PiField(id = \"child\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public ChildState child = new ChildState();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(child, parent);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaSerializers;");
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "public static final PiSchemaField<example.ChildState> CHILD_FIELD = new PiSchemaField<>(CHILD, PiSchemaSerializers.forState(example.ChildState.class));");
        assertMethodContains(compilation, "example.ParentState_PiSchema", "public static CompoundTag saveFull(ParentState self)", "public static CompoundTag saveClientView(ParentState self)", "PiSchemaFieldCodecs.writeField(tag, CHILD_FIELD, self.child);");
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "self.child = PiSchemaFieldCodecs.readFieldInto(__pi_payload, CHILD_FIELD, context, self.child);");
    }

    @Test
    void mergeDeltaModesEmitAdditiveApplyLogicForSetsAndMaps() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MergeState",
                "package example;",
                "import java.util.LinkedHashMap;",
                "import java.util.LinkedHashSet;",
                "import java.util.Map;",
                "import java.util.Set;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldDeltaMode;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:merge_state\", version = 1)",
                "public final class MergeState {",
                "  @PiField(id = \"checkpoints\", sync = PiSyncScope.TRACKING, persist = true, delta = PiFieldDeltaMode.MERGE_SET)",
                "  public final Set<ResourceLocation> checkpoints = new LinkedHashSet<>();",
                "  @PiField(id = \"weights\", sync = PiSyncScope.TRACKING, persist = true, delta = PiFieldDeltaMode.MERGE_MAP)",
                "  public final Map<String, Integer> weights = new LinkedHashMap<>();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertMethodContains(
                compilation,
                "example.MergeState_PiSchema",
                "public static void applyDelta(MergeState self, CompoundTag tag, PiDecodeContext context)",
                "private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type)",
                "self.checkpoints.addAll(__pi_checkpointsDecoded);"
        );
        assertMethodNotContains(
                compilation,
                "example.MergeState_PiSchema",
                "public static void applyDelta(MergeState self, CompoundTag tag, PiDecodeContext context)",
                "private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type)",
                "self.checkpoints.clear();"
        );
        assertMethodContains(
                compilation,
                "example.MergeState_PiSchema",
                "public static void applyDelta(MergeState self, CompoundTag tag, PiDecodeContext context)",
                "private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type)",
                "self.weights.putAll(__pi_weightsDecoded);"
        );
        assertMethodNotContains(
                compilation,
                "example.MergeState_PiSchema",
                "public static void applyDelta(MergeState self, CompoundTag tag, PiDecodeContext context)",
                "private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type)",
                "self.weights.clear();"
        );
    }

    @Test
    void rejectsCustomFieldSerializerWithMismatchedValueType() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MismatchState",
                "package example;",
                "import net.minecraft.world.item.ItemStack;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "@PiSyncModel(id = \"example:mismatch_state\", version = 1)",
                "public final class MismatchState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true, serializer = WrongCodec.class)",
                "  public int count;",
                "  public static final class WrongCodec implements PiFieldCodecProvider<ItemStack> {",
                "    @Override",
                "    public PiSerializer<ItemStack> serializer() {",
                "      return null;",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiField.serializer value type net.minecraft.world.item.ItemStack does not match field type java.lang.Integer");
    }

    @Test
    void rejectsAbstractCustomFieldSerializerProvider() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.AbstractCodecState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "@PiSyncModel(id = \"example:abstract_codec_state\", version = 1)",
                "public final class AbstractCodecState {",
                "  @PiField(id = \"name\", sync = PiSyncScope.TRACKING, persist = true, serializer = AbstractCodec.class)",
                "  public String name = \"fallback\";",
                "  public abstract static class AbstractCodec implements PiFieldCodecProvider<String> {",
                "    @Override",
                "    public PiSerializer<String> serializer() {",
                "      return null;",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiField.serializer must reference a concrete codec provider class");
    }

    @Test
    void rejectsCustomFieldSerializerWithInaccessibleCrossPackageNoArgConstructor() {
        JavaFileObject codec = JavaFileObjects.forSourceLines(
                "codec.ExternalStringCodec",
                "package codec;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "public final class ExternalStringCodec implements PiFieldCodecProvider<String> {",
                "  protected ExternalStringCodec() {",
                "  }",
                "  @Override",
                "  public PiSerializer<String> serializer() {",
                "    return null;",
                "  }",
                "}"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CrossPackageCodecState",
                "package example;",
                "import codec.ExternalStringCodec;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:cross_package_codec_state\", version = 1)",
                "public final class CrossPackageCodecState {",
                "  @PiField(id = \"name\", sync = PiSyncScope.TRACKING, persist = true, serializer = ExternalStringCodec.class)",
                "  public String name = \"fallback\";",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(codec, source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiField.serializer must declare an accessible no-arg constructor");
    }

    @Test
    void rejectsCustomFieldSerializerWithCheckedExceptionNoArgConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedCtorCodecState",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "@PiSyncModel(id = \"example:checked_ctor_codec_state\", version = 1)",
                "public final class CheckedCtorCodecState {",
                "  @PiField(id = \"name\", sync = PiSyncScope.TRACKING, persist = true, serializer = CheckedCodec.class)",
                "  public String name = \"fallback\";",
                "  public static final class CheckedCodec implements PiFieldCodecProvider<String> {",
                "    public CheckedCodec() throws IOException {",
                "    }",
                "    @Override",
                "    public PiSerializer<String> serializer() {",
                "      return null;",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.serializer must declare a no-arg constructor that does not throw checked exceptions"
        );
    }

    @Test
    void rejectsWildcardGenericFieldArguments() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.WildcardListState",
                "package example;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:wildcard_list_state\", version = 1)",
                "public final class WildcardListState {",
                "  @PiField(id = \"values\", sync = PiSyncScope.TRACKING, persist = true)",
                "  public final List<?> values = new ArrayList<>();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("List @PiField types must declare concrete generic arguments");
    }

    @Test
    void resolvesCustomFieldSerializerTypeThroughGenericBaseClass() throws Exception {
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "example.BaseCodec",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "public abstract class BaseCodec<T> implements PiFieldCodecProvider<T> {",
                "  @Override",
                "  public abstract PiSerializer<T> serializer();",
                "}"
        );
        JavaFileObject codec = JavaFileObjects.forSourceLines(
                "example.StringCodec",
                "package example;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "public final class StringCodec extends BaseCodec<String> {",
                "  @Override",
                "  public PiSerializer<String> serializer() {",
                "    return null;",
                "  }",
                "}"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.GenericCodecState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:generic_codec_state\", version = 1)",
                "public final class GenericCodecState {",
                "  @PiField(id = \"name\", sync = PiSyncScope.TRACKING, persist = true, serializer = StringCodec.class)",
                "  public String name = \"fallback\";",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(base, codec, source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.GenericCodecState_PiSchema", "public static final PiSchemaField<java.lang.String> NAME_FIELD = new PiSchemaField<>(NAME, new example.StringCodec().serializer());");
    }

    @Test
    void rejectsStateWithoutAccessibleNoArgConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:invalid_state\", version = 1)",
                "public final class InvalidState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  public InvalidState(int seed) {",
                "    this.count = seed;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSyncModel types must declare an accessible no-arg constructor");
    }

    @Test
    void rejectsStateWithNoArgConstructorThatThrowsCheckedException() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedCtorState",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:checked_ctor_state\", version = 1)",
                "public final class CheckedCtorState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "  public CheckedCtorState() throws IOException {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiSyncModel no-arg constructors must not throw checked exceptions because generated bindings instantiate state hosts directly"
        );
    }

    @Test
    void rejectsAbstractSyncModelTypesBeforeGeneration() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.AbstractState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(id = \"example:abstract_state\", version = 1)",
                "public abstract class AbstractState {",
                "  @PiField(id = \"count\", sync = PiSyncScope.CHUNK, persist = true)",
                "  public int count;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiSyncModel types must be concrete classes");
    }

    @Test
    void generatesLivingFacetDescriptorAndProvider() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLivingFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLivingFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLivingFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLivingFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor;",
                "public abstract class PiGeneratedLivingFacetDescriptor<T, S> implements PiLivingFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLivingFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
                "    this.id = id;",
                "    this.facetClass = facetClass;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() {",
                "    return id;",
                "  }",
                "  @Override",
                "  public Class<T> facetClass() {",
                "    return facetClass;",
                "  }",
                "  @Override",
                "  public Class<S> stateType() {",
                "    return stateType;",
                "  }",
                "  public abstract Capability<T> capability();",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetRegistry {",
                "  void register(PiGeneratedLivingFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetProvider {",
                "  void register(PiLivingFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLivingEntityFacet<S> {",
                "  protected PiStateLivingEntityFacet(PiLivingFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject capability = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public class Capability<T> {",
                "}"
        );
        JavaFileObject capabilityManager = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityManager",
                "package net.minecraftforge.common.capabilities;",
                "public final class CapabilityManager {",
                "  private CapabilityManager() {",
                "  }",
                "  public static <T> Capability<T> get(CapabilityToken<T> token) {",
                "    return new Capability<>();",
                "  }",
                "}"
        );
        JavaFileObject capabilityToken = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.ComboService",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacet;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet;",
                "@PiLivingFacet(namespace = \"example\", path = \"combo\")",
                "public final class ComboService extends PiStateLivingEntityFacet<CounterState> {",
                "  public ComboService(PiLivingFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, capability, capabilityManager, capabilityToken, state, service);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.ComboService_PiLivingDescriptor");
        assertThat(compilation).generatedSourceFile("example.ComboService_PiLivingProvider");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "extends PiGeneratedLivingFacetDescriptor<ComboService, CounterState>");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "private static final class CapabilityHolder {");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "private static final Capability<ComboService> VALUE = CapabilityManager.get(new CapabilityToken<>()");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "super(new ResourceLocation(\"example\", \"combo\"), ComboService.class, CounterState.class);");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "return CapabilityHolder.VALUE;");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "return new ComboService(context);");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingProvider", "implements PiLivingFacetProvider");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingProvider", "registry.register(new ComboService_PiLivingDescriptor());");
    }

    @Test
    void generatesLivingFacetDescriptorWhenStateTypeComesFromGenericSuperclass() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLivingFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLivingFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLivingFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLivingFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor;",
                "public abstract class PiGeneratedLivingFacetDescriptor<T, S> implements PiLivingFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLivingFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
                "    this.id = id;",
                "    this.facetClass = facetClass;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() {",
                "    return id;",
                "  }",
                "  @Override",
                "  public Class<T> facetClass() {",
                "    return facetClass;",
                "  }",
                "  @Override",
                "  public Class<S> stateType() {",
                "    return stateType;",
                "  }",
                "  public abstract Capability<T> capability();",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetRegistry {",
                "  void register(PiGeneratedLivingFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetProvider {",
                "  void register(PiLivingFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLivingEntityFacet<S> {",
                "  protected PiStateLivingEntityFacet(PiLivingFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject playerBase = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStatePlayerFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStatePlayerFacet<S> extends PiStateLivingEntityFacet<S> {",
                "  protected PiStatePlayerFacet(PiLivingFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );
        JavaFileObject capability = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public class Capability<T> {",
                "}"
        );
        JavaFileObject capabilityManager = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityManager",
                "package net.minecraftforge.common.capabilities;",
                "public final class CapabilityManager {",
                "  private CapabilityManager() {",
                "  }",
                "  public static <T> Capability<T> get(CapabilityToken<T> token) {",
                "    return new Capability<>();",
                "  }",
                "}"
        );
        JavaFileObject capabilityToken = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.ComboService",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacet;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStatePlayerFacet;",
                "@PiLivingFacet(namespace = \"example\", path = \"combo\")",
                "public final class ComboService extends PiStatePlayerFacet<CounterState> {",
                "  public ComboService(PiLivingFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(
                        annotation,
                        context,
                        descriptor,
                        generatedDescriptor,
                        registry,
                        provider,
                        base,
                        playerBase,
                        capability,
                        capabilityManager,
                        capabilityToken,
                        state,
                        service
                );

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "CounterState.class");
    }

    @Test
    void rejectsLivingFacetWithParameterizedInferredStateType() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLivingFacetContext {",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLivingEntityFacet<S> {",
                "  protected PiStateLivingEntityFacet(PiLivingFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.ExampleState",
                "package example;",
                "public final class ExampleState<T> {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.ComboService",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacet;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet;",
                "@PiLivingFacet(namespace = \"example\", path = \"combo\")",
                "public final class ComboService extends PiStateLivingEntityFacet<ExampleState<String>> {",
                "  public ComboService(PiLivingFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, base, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLivingFacet types must resolve to a non-parameterized concrete state type"
        );
    }

    @Test
    void rejectsNestedLivingFacetTypesBeforeCodeGeneration() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLivingFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLivingFacetDescriptor<T> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLivingFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor;",
                "public abstract class PiGeneratedLivingFacetDescriptor<T, S> implements PiLivingFacetDescriptor<T> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLivingFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
                "    this.id = id;",
                "    this.facetClass = facetClass;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() {",
                "    return id;",
                "  }",
                "  @Override",
                "  public Class<T> facetClass() {",
                "    return facetClass;",
                "  }",
                "  public Class<S> stateType() {",
                "    return stateType;",
                "  }",
                "  public abstract Capability<T> capability();",
                "  public abstract T create(PiLivingFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetRegistry {",
                "  void register(PiGeneratedLivingFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetProvider {",
                "  void register(PiLivingFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLivingEntityFacet<S> {",
                "  protected PiStateLivingEntityFacet(PiLivingFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject capability = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public class Capability<T> {",
                "}"
        );
        JavaFileObject capabilityManager = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityManager",
                "package net.minecraftforge.common.capabilities;",
                "public final class CapabilityManager {",
                "  private CapabilityManager() {",
                "  }",
                "  public static <T> Capability<T> get(CapabilityToken<T> token) {",
                "    return new Capability<>();",
                "  }",
                "}"
        );
        JavaFileObject capabilityToken = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.OuterServices",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacet;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet;",
                "public final class OuterServices {",
                "  @PiLivingFacet(namespace = \"example\", path = \"combo\")",
                "  public static final class ComboService extends PiStateLivingEntityFacet<CounterState> {",
                "    public ComboService(PiLivingFacetContext context) {",
                "      super(context);",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, capability, capabilityManager, capabilityToken, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLivingFacet types must be top-level classes because generated companions are emitted as package-level types"
        );
    }

    @Test
    void rejectsLivingFacetConstructorThatThrowsCheckedException() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLivingFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLivingFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLivingFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLivingFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetDescriptor;",
                "public abstract class PiGeneratedLivingFacetDescriptor<T, S> implements PiLivingFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLivingFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
                "    this.id = id;",
                "    this.facetClass = facetClass;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() {",
                "    return id;",
                "  }",
                "  @Override",
                "  public Class<T> facetClass() {",
                "    return facetClass;",
                "  }",
                "  @Override",
                "  public Class<S> stateType() {",
                "    return stateType;",
                "  }",
                "  public abstract Capability<T> capability();",
                "  public abstract T create(PiLivingFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetRegistry {",
                "  void register(PiGeneratedLivingFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLivingFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLivingFacetProvider {",
                "  void register(PiLivingFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLivingEntityFacet<S> {",
                "  protected PiStateLivingEntityFacet(PiLivingFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject capability = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public class Capability<T> {",
                "}"
        );
        JavaFileObject capabilityManager = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityManager",
                "package net.minecraftforge.common.capabilities;",
                "public final class CapabilityManager {",
                "  private CapabilityManager() {",
                "  }",
                "  public static <T> Capability<T> get(CapabilityToken<T> token) {",
                "    return new Capability<>();",
                "  }",
                "}"
        );
        JavaFileObject capabilityToken = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.ComboService",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacet;",
                "import org.pickaid.pibrary.api.facet.PiLivingFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet;",
                "@PiLivingFacet(namespace = \"example\", path = \"combo\")",
                "public final class ComboService extends PiStateLivingEntityFacet<CounterState> {",
                "  public ComboService(PiLivingFacetContext context) throws IOException {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, capability, capabilityManager, capabilityToken, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLivingFacet constructors accepting PiLivingFacetContext must not throw checked exceptions because generated descriptors instantiate facets directly"
        );
    }

    private static void assertGeneratedContains(Compilation compilation, String generatedType, String expectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(source.contains(expectedText), () -> "Expected generated source to contain: " + expectedText + "\nActual:\n" + source);
    }

    private static void assertGeneratedNotContains(Compilation compilation, String generatedType, String unexpectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(!source.contains(unexpectedText), () -> "Expected generated source to omit: " + unexpectedText + "\nActual:\n" + source);
    }

    private static void assertMethodContains(
            Compilation compilation,
            String generatedType,
            String methodSignature,
            String nextMarker,
            String expectedText
    ) throws Exception {
        String section = generatedSection(compilation, generatedType, methodSignature, nextMarker);
        assertTrue(section.contains(expectedText), () -> "Expected generated method to contain: " + expectedText + "\nActual:\n" + section);
    }

    private static void assertMethodNotContains(
            Compilation compilation,
            String generatedType,
            String methodSignature,
            String nextMarker,
            String unexpectedText
    ) throws Exception {
        String section = generatedSection(compilation, generatedType, methodSignature, nextMarker);
        assertTrue(!section.contains(unexpectedText), () -> "Expected generated method to omit: " + unexpectedText + "\nActual:\n" + section);
    }

    private static String generatedSection(
            Compilation compilation,
            String generatedType,
            String startMarker,
            String endMarker
    ) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        return start >= 0 && end >= 0 ? source.substring(start, end) : source;
    }
}
