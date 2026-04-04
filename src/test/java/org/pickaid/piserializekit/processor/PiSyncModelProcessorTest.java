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
    void generatesSchemaAndFieldConstants() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.TrialState",
                "package example;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(version = 2)",
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
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "public static final String SCHEMA_ID = \"example.TrialState\";");
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
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaSupport.putStringList(\"players\", self.players)");
        assertGeneratedContains(compilation, "example.TrialState_PiSchema", "PiSchemaSupport.putInt(\"energy\", self.energy)");
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
                "@PiSyncModel(version = 3)",
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

        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaSupport.putBoolean(\"active\", self.active)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaSupport.putString(\"owner_name\", self.ownerName)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaSupport.putUUID(\"run_id\", self.runId)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "PiSchemaSupport.putResourceLocation(\"trial\", self.trial)");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.active = PiSchemaSupport.getBoolean(tag, \"active\", context, self.active);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.ownerName = PiSchemaSupport.getString(tag, \"owner_name\", context, self.ownerName);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.runId = PiSchemaSupport.getUUID(tag, \"run_id\", context, self.runId);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchema", "self.trial = PiSchemaSupport.getResourceLocation(tag, \"trial\", context, self.trial);");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchemaProvider", "public final class AdvancedState_PiSchemaProvider implements PiSchemaProvider");
        assertGeneratedContains(compilation, "example.AdvancedState_PiSchemaProvider", "registry.register(AdvancedState.class, AdvancedState_PiSchema.BINDING);");
    }

    @Test
    void rejectsStateWithoutAccessibleNoArgConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidState",
                "package example;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncModel;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiSyncModel(version = 1)",
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
}
