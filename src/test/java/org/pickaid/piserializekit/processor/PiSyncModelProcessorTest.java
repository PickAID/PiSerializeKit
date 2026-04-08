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
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return ResourceLocation.fromNamespaceAndPath(\"example\", \"trial_state\");");
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
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "return PiSchemaSupport.tagWithHeader(");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "SCHEMA_ID,");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "VERSION,");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static void loadFull(TrialState self, CompoundTag tag, PiDecodeContext context)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static CompoundTag writeDelta(TrialState self, PiDirtySet dirtySet)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static void applyDelta(TrialState self, CompoundTag tag, PiDecodeContext context)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiSchemaField<java.util.List<java.lang.String>> PLAYERS_FIELD = new PiSchemaField<>(PLAYERS, PiSerializers.listOf(requireSerializer(PiSerializers.STRING)));");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final PiSchemaField<java.lang.Integer> ENERGY_FIELD = new PiSchemaField<>(ENERGY, requireSerializer(PiSerializers.INT));");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final List<PiSchemaField<?>> SCHEMA_FIELDS = List.of(PLAYERS_FIELD, ENERGY_FIELD);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaFieldCodecs.writeField(PLAYERS_FIELD, self.players)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaFieldCodecs.writeField(ENERGY_FIELD, self.energy)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "java.util.List<java.lang.String> __pi_playersDecoded = PiSchemaFieldCodecs.readField(tag, PLAYERS_FIELD, context, new java.util.ArrayList<>(self.players));");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "self.players.addAll(__pi_playersDecoded);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "self.energy = PiSchemaFieldCodecs.readField(tag, ENERGY_FIELD, context, self.energy);");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "dirtySet.contains(TrialState_PiFields.PLAYERS)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "dirtySet.contains(TrialState_PiFields.ENERGY)");
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
                "  public ResourceLocation trial = ResourceLocation.parse(\"example:trial\");",
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
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaFieldCodecs.writeField(ACTIVE_FIELD, self.active)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaFieldCodecs.writeField(OWNER_NAME_FIELD, self.ownerName)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaFieldCodecs.writeField(RUN_ID_FIELD, self.runId)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaFieldCodecs.writeField(TRIAL_FIELD, self.trial)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.active = PiSchemaFieldCodecs.readField(tag, ACTIVE_FIELD, context, self.active);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.ownerName = PiSchemaFieldCodecs.readField(tag, OWNER_NAME_FIELD, context, self.ownerName);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.runId = PiSchemaFieldCodecs.readField(tag, RUN_ID_FIELD, context, self.runId);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.trial = PiSchemaFieldCodecs.readField(tag, TRIAL_FIELD, context, self.trial);");
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
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "PiSchemaFieldCodecs.writeField(HEAT_FIELD, self.heat)");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "PiSchemaFieldCodecs.writeField(TIER_FIELD, self.tier)");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "PiSchemaFieldCodecs.writeField(RATIO_FIELD, self.ratio)");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "PiSchemaFieldCodecs.writeField(PRECISION_FIELD, self.precision)");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.heat = PiSchemaFieldCodecs.readField(tag, HEAT_FIELD, context, self.heat);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.tier = PiSchemaFieldCodecs.readField(tag, TIER_FIELD, context, self.tier);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.ratio = PiSchemaFieldCodecs.readField(tag, RATIO_FIELD, context, self.ratio);");
        assertGeneratedContains(compilation, "example.NumericState_PiSchema", "self.precision = PiSchemaFieldCodecs.readField(tag, PRECISION_FIELD, context, self.precision);");
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
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "PiSchemaFieldCodecs.writeField(TARGETS_FIELD, self.targets)");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "java.util.List<net.minecraft.core.BlockPos> __pi_targetsDecoded = PiSchemaFieldCodecs.readField(tag, TARGETS_FIELD, context, new java.util.ArrayList<>(self.targets));");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "self.targets.addAll(__pi_targetsDecoded);");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "self.reward = PiSchemaFieldCodecs.readField(tag, REWARD_FIELD, context, self.reward);");
        assertGeneratedContains(compilation, "example.ComplexState_PiSchema", "self.anchor = PiSchemaFieldCodecs.readField(tag, ANCHOR_FIELD, context, self.anchor);");
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
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "java.util.Set<net.minecraft.resources.ResourceLocation> __pi_checkpointsDecoded = PiSchemaFieldCodecs.readField(tag, CHECKPOINTS_FIELD, context, new java.util.LinkedHashSet<>(self.checkpoints));");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "self.checkpoints.addAll(__pi_checkpointsDecoded);");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "java.util.Map<java.lang.String, java.lang.Integer> __pi_weightsDecoded = PiSchemaFieldCodecs.readField(tag, WEIGHTS_FIELD, context, new java.util.LinkedHashMap<>(self.weights));");
        assertGeneratedContains(compilation, "example.GraphState_PiSchema", "self.weights.putAll(__pi_weightsDecoded);");
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
                "public static void loadFull(VisibilityState self, CompoundTag tag, PiDecodeContext context)",
                "PiSchemaFieldCodecs.writeField(CHUNK_FIELD, self.chunk)"
        );
        assertMethodContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static void loadFull(VisibilityState self, CompoundTag tag, PiDecodeContext context)",
                "PiSchemaFieldCodecs.writeField(TRACKING_FIELD, self.tracking)"
        );
        assertMethodContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static void loadFull(VisibilityState self, CompoundTag tag, PiDecodeContext context)",
                "PiSchemaFieldCodecs.writeField(GLOBAL_FIELD, self.global)"
        );
        assertMethodNotContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static void loadFull(VisibilityState self, CompoundTag tag, PiDecodeContext context)",
                "PiSchemaFieldCodecs.writeField(OWNER_FIELD, self.owner)"
        );
        assertMethodNotContains(
                compilation,
                "example.VisibilityState_PiSchema",
                "public static CompoundTag saveClientView(VisibilityState self)",
                "public static void loadFull(VisibilityState self, CompoundTag tag, PiDecodeContext context)",
                "PiSchemaFieldCodecs.writeField(MENU_FIELD, self.menu)"
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
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "import org.pickaid.piserializekit.runtime.schema.PiSchemaSerializers;");
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "public static final PiSchemaField<example.ChildState> CHILD_FIELD = new PiSchemaField<>(CHILD, PiSchemaSerializers.forState(example.ChildState.class));");
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "PiSchemaFieldCodecs.writeField(CHILD_FIELD, self.child)");
        assertGeneratedContains(compilation, "example.ParentState_PiSchema", "self.child = PiSchemaFieldCodecs.readField(tag, CHILD_FIELD, context, self.child);");
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
    void generatesLivingServiceDescriptorAndProvider() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLivingService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLivingService {",
                "  String namespace();",
                "  String path();",
                "  Class<?> state();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLivingServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLivingServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLivingServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLivingServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  T create(PiLivingServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.service.PiGeneratedLivingServiceDescriptor",
                "package org.pickaid.pibrary.runtime.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.service.PiLivingServiceContext;",
                "import org.pickaid.pibrary.api.service.PiLivingServiceDescriptor;",
                "public abstract class PiGeneratedLivingServiceDescriptor<T, S> implements PiLivingServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLivingServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
                "    this.id = id;",
                "    this.serviceType = serviceType;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() {",
                "    return id;",
                "  }",
                "  @Override",
                "  public Class<T> serviceType() {",
                "    return serviceType;",
                "  }",
                "  @Override",
                "  public Class<S> stateType() {",
                "    return stateType;",
                "  }",
                "  public abstract Capability<T> capability();",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.service.PiLivingServiceRegistry",
                "package org.pickaid.pibrary.runtime.service;",
                "public interface PiLivingServiceRegistry {",
                "  void register(PiGeneratedLivingServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.service.PiLivingServiceProvider",
                "package org.pickaid.pibrary.runtime.service;",
                "public interface PiLivingServiceProvider {",
                "  void register(PiLivingServiceRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiStateLivingEntityService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateLivingEntityService<S> {",
                "  protected PiStateLivingEntityService(PiLivingServiceContext context, Class<S> stateType) {",
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
                "import org.pickaid.pibrary.api.service.PiLivingService;",
                "import org.pickaid.pibrary.api.service.PiLivingServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateLivingEntityService;",
                "@PiLivingService(namespace = \"example\", path = \"combo\", state = CounterState.class)",
                "public final class ComboService extends PiStateLivingEntityService<CounterState> {",
                "  public ComboService(PiLivingServiceContext context) {",
                "    super(context, CounterState.class);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, capability, capabilityManager, capabilityToken, state, service);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.ComboService_PiLivingDescriptor");
        assertThat(compilation).generatedSourceFile("example.ComboService_PiLivingProvider");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "extends PiGeneratedLivingServiceDescriptor<ComboService, CounterState>");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "private static final class CapabilityHolder {");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "private static final Capability<ComboService> VALUE = CapabilityManager.get(new CapabilityToken<>()");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "super(ResourceLocation.fromNamespaceAndPath(\"example\", \"combo\"), ComboService.class, CounterState.class);");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "return CapabilityHolder.VALUE;");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingDescriptor", "return new ComboService(context);");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingProvider", "implements PiLivingServiceProvider");
        assertGeneratedContains(compilation, "example.ComboService_PiLivingProvider", "registry.register(new ComboService_PiLivingDescriptor());");
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
