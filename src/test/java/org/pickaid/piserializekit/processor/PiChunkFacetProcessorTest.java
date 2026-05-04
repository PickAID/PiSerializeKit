package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class PiChunkFacetProcessorTest {
    @Test
    void generatesChunkFacetDescriptorProviderAndCapabilityHolder() throws Exception {
        JavaFileObject state = counterStateType();
        JavaFileObject facet = JavaFileObjects.forSourceLines(
                "example.CounterChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"counter_chunk\")",
                "public final class CounterChunkFacet extends PiStateChunkFacet<CounterState> {",
                "  public CounterChunkFacet(PiChunkFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = compileChunkFacet(statefulChunkFacetBase(), state, facet);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.CounterChunkFacet_PiChunkDescriptor");
        assertThat(compilation).generatedSourceFile("example.CounterChunkFacet_PiChunkProvider");
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkDescriptor",
                "extends PiGeneratedChunkFacetDescriptor<CounterChunkFacet, CounterState>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkDescriptor",
                "super(new ResourceLocation(\"example\", \"counter_chunk\"), CounterChunkFacet.class, CounterState.class);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkDescriptor",
                "private static final class CapabilityHolder"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkDescriptor",
                "Capability<CounterChunkFacet>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkDescriptor",
                "return new CounterChunkFacet(context);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkProvider",
                "implements PiChunkFacetProvider"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkFacet_PiChunkProvider",
                "registry.register(new CounterChunkFacet_PiChunkDescriptor());"
        );
        assertGeneratedResourceContains(
                compilation,
                "META-INF/services/org.pickaid.pibrary.runtime.facet.PiChunkFacetProvider",
                "example.CounterChunkFacet_PiChunkProvider"
        );
    }

    @Test
    void rejectsChunkFacetWithoutAccessibleContextConstructor() {
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateChunkFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateChunkFacet<S> {",
                "  protected PiStateChunkFacet() {",
                "  }",
                "}"
        );
        JavaFileObject state = counterStateType();
        JavaFileObject facet = JavaFileObjects.forSourceLines(
                "example.MissingConstructorChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"missing_constructor\")",
                "public final class MissingConstructorChunkFacet extends PiStateChunkFacet<CounterState> {",
                "}"
        );

        Compilation compilation = compileChunkFacet(base, state, facet);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiChunkFacet types must declare an accessible constructor accepting PiChunkFacetContext"
        );
    }

    @Test
    void rejectsChunkFacetWithInvalidNamespaceOrPath() {
        JavaFileObject state = counterStateType();
        JavaFileObject facet = JavaFileObjects.forSourceLines(
                "example.InvalidLocationChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"bad namespace\", path = \"counter chunk\")",
                "public final class InvalidLocationChunkFacet extends PiStateChunkFacet<CounterState> {",
                "  public InvalidLocationChunkFacet(PiChunkFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = compileChunkFacet(statefulChunkFacetBase(), state, facet);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiChunkFacet requires namespace and path values to form a valid namespace:path resource location"
        );
    }

    @Test
    void rejectsChunkFacetWithParameterizedInferredStateType() {
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.ExampleState",
                "package example;",
                "public final class ExampleState<T> {",
                "}"
        );
        JavaFileObject facet = JavaFileObjects.forSourceLines(
                "example.ParameterizedStateChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"parameterized_state\")",
                "public final class ParameterizedStateChunkFacet extends PiStateChunkFacet<ExampleState<String>> {",
                "  public ParameterizedStateChunkFacet(PiChunkFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = compileChunkFacet(statefulChunkFacetBase(), state, facet);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiChunkFacet types must resolve to a non-parameterized concrete state type"
        );
    }

    @Test
    void rejectsChunkFacetConstructorThatThrowsCheckedException() {
        JavaFileObject state = counterStateType();
        JavaFileObject facet = JavaFileObjects.forSourceLines(
                "example.CheckedExceptionChunkFacet",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"checked_exception\")",
                "public final class CheckedExceptionChunkFacet extends PiStateChunkFacet<CounterState> {",
                "  public CheckedExceptionChunkFacet(PiChunkFacetContext context) throws IOException {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = compileChunkFacet(statefulChunkFacetBase(), state, facet);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiChunkFacet constructors accepting PiChunkFacetContext must not throw checked exceptions because generated descriptors instantiate facets directly"
        );
    }

    @Test
    void rejectsDuplicateChunkFacetIdsAcrossTypes() {
        JavaFileObject state = counterStateType();
        JavaFileObject first = JavaFileObjects.forSourceLines(
                "example.FirstChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"shared_chunk\")",
                "public final class FirstChunkFacet extends PiStateChunkFacet<CounterState> {",
                "  public FirstChunkFacet(PiChunkFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );
        JavaFileObject second = JavaFileObjects.forSourceLines(
                "example.SecondChunkFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacet;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateChunkFacet;",
                "@PiChunkFacet(namespace = \"example\", path = \"shared_chunk\")",
                "public final class SecondChunkFacet extends PiStateChunkFacet<CounterState> {",
                "  public SecondChunkFacet(PiChunkFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = compileChunkFacet(statefulChunkFacetBase(), state, first, second);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Duplicate Pi chunk facet id example:shared_chunk");
    }

    private static Compilation compileChunkFacet(JavaFileObject base, JavaFileObject state, JavaFileObject... facets) {
        List<JavaFileObject> sources = new ArrayList<>(List.of(
                chunkFacetAnnotation(),
                chunkFacetContext(),
                chunkFacetDescriptor(),
                generatedChunkFacetDescriptor(),
                chunkFacetRegistry(),
                chunkFacetProvider(),
                base,
                resourceLocationType(),
                capabilityType(),
                capabilityTokenType(),
                capabilityManagerType(),
                state
        ));
        sources.addAll(List.of(facets));
        return javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(sources);
    }

    private static JavaFileObject chunkFacetAnnotation() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiChunkFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiChunkFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
    }

    private static JavaFileObject chunkFacetContext() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiChunkFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiChunkFacetContext {",
                "}"
        );
    }

    private static JavaFileObject chunkFacetDescriptor() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiChunkFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiChunkFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiChunkFacetContext context);",
                "}"
        );
    }

    private static JavaFileObject generatedChunkFacetDescriptor() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedChunkFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiChunkFacetDescriptor;",
                "public abstract class PiGeneratedChunkFacetDescriptor<T, S> implements PiChunkFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedChunkFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
                "    this.id = id;",
                "    this.facetClass = facetClass;",
                "    this.stateType = stateType;",
                "  }",
                "  @Override",
                "  public ResourceLocation id() { return id; }",
                "  @Override",
                "  public Class<T> facetClass() { return facetClass; }",
                "  @Override",
                "  public Class<S> stateType() { return stateType; }",
                "  public abstract Capability<T> capability();",
                "  public abstract T create(PiChunkFacetContext context);",
                "}"
        );
    }

    private static JavaFileObject chunkFacetRegistry() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiChunkFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiChunkFacetRegistry {",
                "  void register(PiGeneratedChunkFacetDescriptor<?, ?> descriptor);",
                "}"
        );
    }

    private static JavaFileObject chunkFacetProvider() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiChunkFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiChunkFacetProvider {",
                "  void register(PiChunkFacetRegistry registry);",
                "}"
        );
    }

    private static JavaFileObject statefulChunkFacetBase() {
        return JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateChunkFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateChunkFacet<S> {",
                "  protected PiStateChunkFacet(PiChunkFacetContext context) {",
                "  }",
                "}"
        );
    }

    private static JavaFileObject resourceLocationType() {
        return JavaFileObjects.forSourceLines(
                "net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources;",
                "public record ResourceLocation(String namespace, String path) {",
                "  public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {",
                "    return new ResourceLocation(namespace, path);",
                "  }",
                "}"
        );
    }

    private static JavaFileObject capabilityType() {
        return JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public interface Capability<T> {",
                "}"
        );
    }

    private static JavaFileObject capabilityTokenType() {
        return JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
    }

    private static JavaFileObject capabilityManagerType() {
        return JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityManager",
                "package net.minecraftforge.common.capabilities;",
                "public final class CapabilityManager {",
                "  private CapabilityManager() {",
                "  }",
                "  public static <T> Capability<T> get(CapabilityToken<T> token) {",
                "    return null;",
                "  }",
                "}"
        );
    }

    private static JavaFileObject counterStateType() {
        return JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
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
