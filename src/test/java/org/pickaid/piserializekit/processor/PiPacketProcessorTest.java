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
                "import org.pickaid.piserializekit.api.packet.PiServerPacketContext;",
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
                "  @Override protected void handle(PiServerPacketContext context) { }",
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
                "public static final PiPacketBinding<CastSkillRequest, PiServerPacketContext> BINDING = new PiPacketBinding<>() {"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final List<PiFieldKey> FIELDS = List.of(SKILL_ID, TARGET);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(\"example\", \"cast_skill\");"
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
                "net.minecraft.resources.ResourceLocation __pi_raw_skillId = PiPacketSupport.readIncomingField(buffer, SKILL_ID.id(), SKILL_ID_SERIALIZER, context, legacy, ResourceLocation.fromNamespaceAndPath(\"minecraft\", \"empty\"));"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "net.minecraft.core.BlockPos __pi_target = PiSchemaFieldCodecs.decode(resolvedPayload, TARGET.id(), TARGET_SERIALIZER, context.child(TARGET.id()), net.minecraft.core.BlockPos.ZERO);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "return new CastSkillRequest(__pi_skillId, __pi_target);"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "throw new PiPacketDecodeException(PACKET_ID, context.result());"
        );
        assertGeneratedContains(
                compilation,
                "example.CastSkillRequest_PiPacket",
                "packet.handle(context);"
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
    void generatesPacketBindingWithExplicitPacketId() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.OpenMenuToClient",
                "package example;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacket;",
                "import org.pickaid.piserializekit.api.packet.PiClientPacketContext;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.schema.PiField;",
                "import org.pickaid.piserializekit.api.schema.PiSyncScope;",
                "@PiPacket(id = \"demo:open_menu\")",
                "public final class OpenMenuToClient extends PiClientPacket {",
                "  @PiField(id = \"menu_id\", sync = PiSyncScope.OWNER, persist = false) public ResourceLocation menuId;",
                "  public OpenMenuToClient(ResourceLocation menuId) {",
                "    this.menuId = menuId;",
                "  }",
                "  @Override protected void handle(PiClientPacketContext context) { }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertGeneratedContains(
                compilation,
                "example.OpenMenuToClient_PiPacket",
                "public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(\"demo\", \"open_menu\");"
        );
        assertGeneratedContains(
                compilation,
                "example.OpenMenuToClient_PiPacket",
                "return PiPacketDirection.CLIENTBOUND;"
        );
    }

    @Test
    void failsWhenPacketNamespaceCannotBeResolved() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "example.MissingNamespacePacket",
                "package example;",
                "import org.pickaid.piserializekit.api.packet.PiPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacket;",
                "import org.pickaid.piserializekit.api.packet.PiServerPacketContext;",
                "@PiPacket",
                "public final class MissingNamespacePacket extends PiServerPacket {",
                "  public MissingNamespacePacket() {",
                "  }",
                "  @Override protected void handle(PiServerPacketContext context) { }",
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

    private static void assertGeneratedContains(Compilation compilation, String generatedType, String expectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedSourceFile(generatedType).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(source.contains(expectedText), () -> "Expected generated source to contain: " + expectedText + "\nActual:\n" + source);
    }

    private static void assertGeneratedResourceContains(Compilation compilation, String path, String expectedText) throws Exception {
        JavaFileObject fileObject = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, path).orElseThrow();
        String source = fileObject.getCharContent(false).toString();
        assertTrue(source.contains(expectedText), () -> "Expected generated resource to contain: " + expectedText + "\nActual:\n" + source);
    }
}
