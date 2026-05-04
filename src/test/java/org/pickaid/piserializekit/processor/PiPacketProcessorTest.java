package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class PiPacketProcessorTest {
    @Test
    void packetGenerationDoesNotRequireHandleMethodOrGenerateDispatchMethod() throws Exception {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField(id = \"skill_id\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation skillId;",
                "  @PiField(id = \"target\", sync = PiSyncScope.OWNER, persist = false) public BlockPos target;",
                "  public CastSkillRequest(ResourceLocation skillId, BlockPos target) {",
                "    this.skillId = skillId;",
                "    this.target = target;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final PiPacketBinding<CastSkillRequest> BINDING = new PiPacketBinding<>() {"
        );
        assertGeneratedDoesNotContain(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "dispatch("
        );
    }

    @Test
    void generatesPacketBindingWithPackageNamespaceAndInferredPathAndConstructorDispatch() throws Exception {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField(id = \"skill_id\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation skillId;",
                "  @PiField(id = \"target\", sync = PiSyncScope.OWNER, persist = false) public BlockPos target;",
                "  public CastSkillRequest(ResourceLocation skillId, BlockPos target) {",
                "    this.skillId = skillId;",
                "    this.target = target;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.CastSkillRequest_PiPacket");
        assertThat(compilation).generatedSourceFile("example.CastSkillRequest_PiPacketProvider");

        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final PiPacketBinding<CastSkillRequest> BINDING = new PiPacketBinding<>() {"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final List<PiFieldKey> FIELDS = List.of(SKILL_ID, TARGET);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final ResourceLocation PACKET_ID = new ResourceLocation(\"example\", \"cast_skill\");"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "return PiPacketDirection.SERVERBOUND;"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public List<PiFieldKey> fields() {"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "buffer.writeVarInt(VERSION);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "int incomingVersion = PiPacketSupport.safeRead(context, PiSchemaSupport.SCHEMA_VERSION_KEY, buffer::readVarInt, VERSION);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "if (!legacy) {"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "return new CastSkillRequest(__pi_raw_skillId, __pi_raw_target);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "net.minecraft.resources.ResourceLocation __pi_raw_skillId = PiPacketSupport.readIncomingField(buffer, SKILL_ID.id(), SKILL_ID_SERIALIZER, context, legacy, new ResourceLocation(\"minecraft\", \"empty\"));"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "net.minecraft.core.BlockPos __pi_target = PiSchemaFieldCodecs.decode(resolvedPayload, TARGET.id(), TARGET_SERIALIZER, context, net.minecraft.core.BlockPos.ZERO);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "return new CastSkillRequest(__pi_skillId, __pi_target);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet construction failed\"), true);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet decode failed\"), true);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "throw new PiPacketDecodeException(PACKET_ID, context.result());"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacketProvider",
                "registry.register(CastSkillRequest.class, CastSkillRequest_PiPacket.BINDING);"
        );
        assertGeneratedResourceContains(
                compilation,
                "META-INF/services/org.pickaid.piserializekit.api.packet.PiPacketProvider",
                "example.CastSkillRequest_PiPacketProvider"
        );
    }

    @Test
    void inferredPacketPathTrimsStackedDirectionalAndPacketSuffixes() throws Exception {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.ShowToastToClientPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "@PiPacket",
                "public final class ShowToastToClientPacket extends PiClientPacket {",
                "  public ShowToastToClientPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.ShowToastToClientPacket_PiPacket",
                "public static final ResourceLocation PACKET_ID = new ResourceLocation(\"example\", \"show_toast\");"
        );
    }

    @Test
    void inferredPacketPathAlsoTrimsDirectionalPayloadSuffixes() throws Exception {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillToServerPayload",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket",
                "public final class CastSkillToServerPayload extends PiServerPacket {",
                "  public CastSkillToServerPayload() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.CastSkillToServerPayload_PiPacket",
                "public static final ResourceLocation PACKET_ID = new ResourceLocation(\"example\", \"cast_skill\");"
        );
    }

    @Test
    void generatesPacketBindingWithExplicitPacketId() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.OpenMenuToClient",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"demo:open_menu\")",
                "public final class OpenMenuToClient extends PiClientPacket {",
                "  @PiField(id = \"menu_id\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation menuId;",
                "  public OpenMenuToClient(ResourceLocation menuId) {",
                "    this.menuId = menuId;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.OpenMenuToClient_PiPacket",
                "public static final ResourceLocation PACKET_ID = new ResourceLocation(\"demo\", \"open_menu\");"
        );
        assertGeneratedContains(
                compilation,
                "example.OpenMenuToClient_PiPacket",
                "return PiPacketDirection.CLIENTBOUND;"
        );
    }

    @Test
    void generatesPacketBindingFromCompactPiFieldAuthoring() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:cast_skill\")",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField public ResourceLocation skillId;",
                "  @PiField public BlockPos target;",
                "  @PiField public boolean shiftDown;",
                "  public CastSkillRequest(ResourceLocation skillId, BlockPos target, boolean shiftDown) {",
                "    this.skillId = skillId;",
                "    this.target = target;",
                "    this.shiftDown = shiftDown;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final List<PiFieldKey> FIELDS = List.of(SKILL_ID, TARGET, SHIFT_DOWN);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final PiFieldKey SKILL_ID = new PiFieldKey(0, \"skill_id\");"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final PiFieldKey TARGET = new PiFieldKey(1, \"target\");"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final PiFieldKey SHIFT_DOWN = new PiFieldKey(2, \"shift_down\");"
        );
    }

    @Test
    void rejectsNestedPacketTypesBeforeCodeGeneration() {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.OuterPackets",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "public final class OuterPackets {",
                "  @PiPacket",
                "  public static final class CastSkillRequest extends PiServerPacket {",
                "    public CastSkillRequest() {",
                "    }",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket types must be top-level classes because generated companions are emitted as package-level types"
        );
    }

    @Test
    void failsWhenPacketNamespaceCannotBeResolved() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MissingNamespacePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket",
                "public final class MissingNamespacePacket extends PiServerPacket {",
                "  public MissingNamespacePacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket requires a namespace via @PiPacket(namespace = ...), @PiPacket(id = ...), or package-info @PiPacketNamespace(...)"
        );
    }

    @Test
    void rejectsPacketWithNonPositiveVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidVersionPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(id = \"example:invalid_version\", version = 0)",
                "public final class InvalidVersionPacket extends PiServerPacket {",
                "  public InvalidVersionPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacket.version must be >= 1");
    }

    @Test
    void rejectsBlankExplicitPacketId() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.BlankIdPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(id = \"\")",
                "public final class BlankIdPacket extends PiServerPacket {",
                "  public BlankIdPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacket.id must be non-blank when declared");
    }

    @Test
    void rejectsBlankExplicitPacketNamespace() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.BlankNamespacePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(namespace = \"\", path = \"show_toast\")",
                "public final class BlankNamespacePacket extends PiServerPacket {",
                "  public BlankNamespacePacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacket.namespace must be non-blank when declared");
    }

    @Test
    void rejectsInvalidExplicitPacketNamespaceAndReportsActualValue() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidNamespacePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(namespace = \"Example Mod\", path = \"show_toast\")",
                "public final class InvalidNamespacePacket extends PiServerPacket {",
                "  public InvalidNamespacePacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket.namespace must be a valid resource namespace: Example Mod"
        );
    }

    @Test
    void rejectsBlankExplicitPacketPath() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.BlankPathPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(namespace = \"example\", path = \"\")",
                "public final class BlankPathPacket extends PiServerPacket {",
                "  public BlankPathPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacket.path must be non-blank when declared");
    }

    @Test
    void rejectsInvalidExplicitPacketPathAndReportsActualValue() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidPathPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(namespace = \"example\", path = \"Show Toast\")",
                "public final class InvalidPathPacket extends PiServerPacket {",
                "  public InvalidPathPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket.path must resolve to a valid resource path: Show Toast"
        );
    }

    @Test
    void rejectsInvalidPackagePacketNamespaceAndReportsActualValue() {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"Example Mod\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidPackageNamespacePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket",
                "public final class InvalidPackageNamespacePacket extends PiServerPacket {",
                "  public InvalidPackageNamespacePacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacketNamespace value must be a valid resource namespace: Example Mod"
        );
    }

    @Test
    void rejectsAbstractPacketTypesBeforeGeneration() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.AbstractNoticePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(id = \"example:abstract_notice\")",
                "public abstract class AbstractNoticePacket extends PiServerPacket {",
                "  public AbstractNoticePacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacket types must be concrete classes");
    }

    @Test
    void rejectsDuplicateExplicitPacketIdsAcrossPackets() {
        JavaFileObject first = JavaFileObjects.forSourceLines(
                "example.OpenMenuToClient",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "@PiPacket(id = \"demo:open_menu\")",
                "public final class OpenMenuToClient extends PiClientPacket {",
                "  public OpenMenuToClient() {",
                "  }",
                "}"
        );
        JavaFileObject second = JavaFileObjects.forSourceLines(
                "example.OpenMenuToServer",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(id = \"demo:open_menu\")",
                "public final class OpenMenuToServer extends PiServerPacket {",
                "  public OpenMenuToServer() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(first, second);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Duplicate Pi packet id demo:open_menu");
    }

    @Test
    void rejectsCombiningExplicitPacketIdWithNamespaceOverride() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidPacketIdentityPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket(id = \"demo:open_menu\", namespace = \"example\")",
                "public final class InvalidPacketIdentityPacket extends PiServerPacket {",
                "  public InvalidPacketIdentityPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket.id cannot be combined with @PiPacket.namespace or @PiPacket.path"
        );
    }

    @Test
    void rejectsInferredPacketPathCollisionsWithinOneNamespace() {
        JavaFileObject packageInfo = JavaFileObjects.forSourceLines(
                "example.package-info",
                "@PiPacketNamespace(\"example\")",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacketNamespace;"
        );
        JavaFileObject first = JavaFileObjects.forSourceLines(
                "example.OpenMenuRequest",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket",
                "public final class OpenMenuRequest extends PiServerPacket {",
                "  public OpenMenuRequest() {",
                "  }",
                "}"
        );
        JavaFileObject second = JavaFileObjects.forSourceLines(
                "example.OpenMenuPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "@PiPacket",
                "public final class OpenMenuPacket extends PiServerPacket {",
                "  public OpenMenuPacket() {",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(packageInfo, first, second);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Duplicate Pi packet id example:open_menu");
    }

    @Test
    void reportsExpectedConstructorSignatureWhenPacketConstructorDoesNotMatchFieldOrder() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.ShowToastPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"example:show_toast\")",
                "public final class ShowToastPacket extends PiClientPacket {",
                "  @PiField(id = \"title\", sync = PiSyncScope.OWNER, persist = false) public String title;",
                "  @PiField(id = \"priority\", sync = PiSyncScope.OWNER, persist = false) public int priority;",
                "  public ShowToastPacket(int priority, String title) {",
                "    this.priority = priority;",
                "    this.title = title;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket types must declare an accessible constructor matching @PiField order: (java.lang.String title, java.lang.Integer priority)"
        );
    }

    @Test
    void rejectsPacketConstructorThatThrowsCheckedException() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedCtorPacket",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:checked_ctor_packet\")",
                "public final class CheckedCtorPacket extends PiServerPacket {",
                "  @PiField",
                "  public final int count;",
                "  public CheckedCtorPacket(int count) throws IOException {",
                "    this.count = count;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket constructors matching @PiField order must not throw checked exceptions because generated bindings instantiate packets directly"
        );
    }

    @Test
    void rejectsAmbiguousPacketConstructorWhenRepeatedFieldTypesDoNotMatchFieldNames() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.DualSkillPacket",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:dual_skill\")",
                "public final class DualSkillPacket extends PiServerPacket {",
                "  @PiField public ResourceLocation primarySkillId;",
                "  @PiField public ResourceLocation secondarySkillId;",
                "  public DualSkillPacket(ResourceLocation secondarySkillId, ResourceLocation primarySkillId) {",
                "    this.primarySkillId = primarySkillId;",
                "    this.secondarySkillId = secondarySkillId;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacket types must declare an accessible constructor matching @PiField order: "
                        + "(net.minecraft.resources.ResourceLocation primarySkillId, "
                        + "net.minecraft.resources.ResourceLocation secondarySkillId)"
        );
    }

    @Test
    void rejectsPacketMigrationChainsWithPreciseGapDiagnostics() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:legacy_skill\", version = 5)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  public LegacySkillPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "  @PiPacketUpgrade(from = 4, to = 5)",
                "  static CompoundTag upgradeV4ToV5(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiPacketUpgrade chain must define a migration path from version 1 to 5; missing step from version 2. Declared steps: 1->2, 4->5"
        );
    }

    @Test
    void reportsExpectedPacketUpgradeMethodShape() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "@PiPacket(id = \"example:legacy_skill\", version = 2)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  public LegacySkillPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 2)",
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
                "@PiPacketUpgrade methods must match: static CompoundTag method(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context); found: CompoundTag upgradeV1ToV2(CompoundTag payload, PiDecodeContext context)"
        );
    }

    @Test
    void rejectsPacketUpgradeMethodThatThrowsCheckedException() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedUpgradePacket",
                "package example;",
                "import java.io.IOException;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:checked_upgrade_packet\", version = 2)",
                "public final class CheckedUpgradePacket extends PiServerPacket {",
                "  public CheckedUpgradePacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 2)",
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
                "@PiPacketUpgrade methods must not throw checked exceptions because generated migrations invoke them directly"
        );
    }

    @Test
    void generatesPacketBindingMigrationMetadata() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.nbt.CompoundTag;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:legacy_skill\", version = 2)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  @PiField public ResourceLocation skillId;",
                "  @PiField public BlockPos target;",
                "  public LegacySkillPacket(ResourceLocation skillId, BlockPos target) {",
                "    this.skillId = skillId;",
                "    this.target = target;",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(compilation, "example.LegacySkillPacket_PiPacket", "public List<PiSchemaMigration> migrations() {");
        assertGeneratedContains(compilation, "example.LegacySkillPacket_PiPacket", "return MIGRATIONS;");
    }

    @Test
    void reportsPacketMigrationMethodWhenTargetVersionOvershootsDeclaredVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:legacy_skill\", version = 3)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  public LegacySkillPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 4)",
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
                "@PiPacketUpgrade step upgradeV1ToV4 declares to=4 above declared version 3"
        );
    }

    @Test
    void reportsPacketMigrationMethodWhenSourceVersionIsAtOrAboveDeclaredVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:legacy_skill\", version = 3)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  public LegacySkillPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 3, to = 4)",
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
                "@PiPacketUpgrade step upgradeV3ToV4 declares from=3 at or above declared version 3"
        );
    }

    @Test
    void rejectsPacketMigrationStepsWithInvalidBounds() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.InvalidPacketMigrationBoundsPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:invalid_packet_migration_bounds\", version = 2)",
                "public final class InvalidPacketMigrationBoundsPacket extends PiServerPacket {",
                "  public InvalidPacketMigrationBoundsPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 0, to = 0)",
                "  static CompoundTag upgradeV0ToV0(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@PiPacketUpgrade requires to > from >= 1");
    }

    @Test
    void reportsConflictingPacketMigrationMethodsForSameSourceVersion() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.LegacySkillPacket",
                "package example;",
                "import net.minecraft.nbt.CompoundTag;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiDecodeContext;",
                "import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;",
                "@PiPacket(id = \"example:legacy_skill\", version = 3)",
                "public final class LegacySkillPacket extends PiServerPacket {",
                "  public LegacySkillPacket() {",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 2)",
                "  static CompoundTag upgradeV1ToV2(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context) {",
                "    return payload;",
                "  }",
                "  @PiPacketUpgrade(from = 1, to = 3)",
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
                "@PiPacketUpgrade allows only one migration step from version 1; existing method: upgradeV1ToV2, conflicting method: upgradeV1ToV3"
        );
    }

    @Test
    void rejectsPacketFieldIdsThatAreNotStablePayloadKeys() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"example:cast_skill\")",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField(id = \"Skill Id\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation skillId;",
                "  public CastSkillRequest(ResourceLocation skillId) {",
                "    this.skillId = skillId;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id for field skillId must resolve to a valid payload key"
        );
    }

    @Test
    void rejectsPacketFieldIdsUsingReservedFrameworkPrefix() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"example:cast_skill\")",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField(id = \"__pi_version\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation skillId;",
                "  public CastSkillRequest(ResourceLocation skillId) {",
                "    this.skillId = skillId;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id for field skillId uses reserved Pi payload prefix __pi_"
        );
    }

    @Test
    void rejectsInferredPacketFieldIdsUsingReservedFrameworkPrefix() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:cast_skill\")",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField public ResourceLocation __piVersion;",
                "  public CastSkillRequest(ResourceLocation __piVersion) {",
                "    this.__piVersion = __piVersion;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.id for field __piVersion uses reserved Pi payload prefix __pi_"
        );
    }

    @Test
    void rejectsPacketFieldsThatOptIntoPersistenceMetadata() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.PersistentPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"example:persistent_packet\")",
                "public final class PersistentPacket extends PiServerPacket {",
                "  @PiField(id = \"count\", sync = PiSyncScope.OWNER, persist = true)",
                "  public int count;",
                "  public PersistentPacket(int count) {",
                "    this.count = count;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.persist on @PiPacket field count must stay false because packet fields are transport-only"
        );
    }

    @Test
    void rejectsPacketFieldsThatDeclareNonOwnerSyncScope() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.TrackingPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"example:tracking_packet\")",
                "public final class TrackingPacket extends PiServerPacket {",
                "  @PiField(id = \"count\", sync = PiSyncScope.TRACKING, persist = false)",
                "  public int count;",
                "  public TrackingPacket(int count) {",
                "    this.count = count;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.sync on @PiPacket field count must stay OWNER because packet fields do not participate in state visibility routing"
        );
    }

    @Test
    void rejectsPacketFieldsThatDeclareNonReplaceDeltaMode() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MergePacket",
                "package example;",
                "import java.util.LinkedHashSet;",
                "import java.util.Set;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldDeltaMode;",
                "@PiPacket(id = \"example:merge_packet\")",
                "public final class MergePacket extends PiServerPacket {",
                "  @PiField(delta = PiFieldDeltaMode.MERGE_SET)",
                "  public final Set<String> tags = new LinkedHashSet<>();",
                "  public MergePacket(Set<String> tags) {",
                "    this.tags.addAll(tags);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiField.delta on @PiPacket field tags must stay REPLACE because packet payloads do not apply field deltas"
        );
    }

    @Test
    void supportsImmutableScalarPacketFieldsBackedByConstructorDecode() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CastSkillRequest",
                "package example;",
                "import net.minecraft.core.BlockPos;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(namespace = \"example\", path = \"cast_skill\")",
                "public final class CastSkillRequest extends PiServerPacket {",
                "  @PiField(id = \"skill_id\", sync = PiSyncScope.OWNER, persist = false) public final ResourceLocation skillId;",
                "  @PiField(id = \"target\", sync = PiSyncScope.OWNER, persist = false) public final BlockPos target;",
                "  public CastSkillRequest(ResourceLocation skillId, BlockPos target) {",
                "    this.skillId = skillId;",
                "    this.target = target;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "return new CastSkillRequest(__pi_raw_skillId, __pi_raw_target);"
        );
    }

    @Test
    void supportsPacketCustomFieldSerializerTypeThroughGenericBaseClass() throws Exception {
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
                "example.ShowToastPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:show_toast\")",
                "public final class ShowToastPacket extends PiClientPacket {",
                "  @PiField(serializer = StringCodec.class)",
                "  public final String title;",
                "  public ShowToastPacket(String title) {",
                "    this.title = title;",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(base, codec, source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.ShowToastPacket_PiPacket",
                "public static final PiSerializer<java.lang.String> TITLE_SERIALIZER = new example.StringCodec().serializer();"
        );
    }

    @Test
    void rejectsPacketCustomFieldSerializerWithMismatchedValueType() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MismatchPacket",
                "package example;",
                "import net.minecraft.world.item.ItemStack;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "@PiPacket(id = \"example:mismatch_packet\")",
                "public final class MismatchPacket extends PiServerPacket {",
                "  @PiField(serializer = WrongCodec.class)",
                "  public final int count;",
                "  public MismatchPacket(int count) {",
                "    this.count = count;",
                "  }",
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
        assertThat(compilation).hadErrorContaining(
                "@PiField.serializer value type net.minecraft.world.item.ItemStack does not match field type java.lang.Integer"
        );
    }

    @Test
    void rejectsPacketCustomFieldSerializerWithCheckedExceptionNoArgConstructor() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.CheckedCtorPacket",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;",
                "import org.pickaid.piserializekit.api.service.PiSerializer;",
                "@PiPacket(id = \"example:checked_ctor_packet\")",
                "public final class CheckedCtorPacket extends PiServerPacket {",
                "  @PiField(serializer = CheckedCodec.class)",
                "  public final String title;",
                "  public CheckedCtorPacket(String title) {",
                "    this.title = title;",
                "  }",
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
    void rejectsPrivatePacketFieldsBeforeGeneratedCodeCompilation() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.PrivatePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:private_packet\")",
                "public final class PrivatePacket extends PiServerPacket {",
                "  @PiField",
                "  private int count;",
                "  public PrivatePacket(int count) {",
                "    this.count = count;",
                "  }",
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
    void rejectsStaticPacketFieldsInsteadOfIgnoringThem() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.StaticPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:static_packet\")",
                "public final class StaticPacket extends PiServerPacket {",
                "  @PiField",
                "  public static int count;",
                "  public StaticPacket() {",
                "  }",
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
    void rejectsTransientPacketFieldsBecausePiFieldAlreadyOwnsTransportSemantics() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.TransientPacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "@PiPacket(id = \"example:transient_packet\")",
                "public final class TransientPacket extends PiServerPacket {",
                "  @PiField",
                "  public transient int count;",
                "  public TransientPacket(int count) {",
                "    this.count = count;",
                "  }",
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

    private static void assertGeneratedContains(Compilation compilation, String generatedType, String expectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(source.contains(expectedText), () -> "Expected generated source to contain: " + expectedText + "\nActual:\n" + source);
    }

    private static void assertGeneratedDoesNotContain(Compilation compilation, String generatedType, String unexpectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(!source.contains(unexpectedText), () -> "Expected generated source to omit: " + unexpectedText + "\nActual:\n" + source);
    }

    private static void assertGeneratedResourceContains(Compilation compilation, String path, String expectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, path).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(source.contains(expectedText), () -> "Expected generated resource to contain: " + expectedText + "\nActual:\n" + source);
    }
}
