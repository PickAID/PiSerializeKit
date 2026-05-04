package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.StandardLocation;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class PiLevelFacetProcessorTest {
    @Test
    void generatesLevelFacetDescriptorAndProvider() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLevelFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLevelFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor;",
                "public abstract class PiGeneratedLevelFacetDescriptor<T, S> implements PiLevelFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetRegistry {",
                "  void register(PiGeneratedLevelFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetProvider {",
                "  void register(PiLevelFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLevelFacet<S> {",
                "  protected PiStateLevelFacet(PiLevelFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject resourceLocation = JavaFileObjects.forSourceLines(
                "net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources;",
                "public record ResourceLocation(String namespace, String path) {",
                "  public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {",
                "    return new ResourceLocation(namespace, path);",
                "  }",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.CounterLevelFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacet;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLevelFacet;",
                "@PiLevelFacet(namespace = \"example\", path = \"counter_level\")",
                "public final class CounterLevelFacet extends PiStateLevelFacet<CounterState> {",
                "  public CounterLevelFacet(PiLevelFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, resourceLocation, state, service);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.CounterLevelFacet_PiLevelDescriptor");
        assertThat(compilation).generatedSourceFile("example.CounterLevelFacet_PiLevelProvider");
        assertGeneratedContains(
                compilation,
                "example.CounterLevelFacet_PiLevelDescriptor",
                "extends PiGeneratedLevelFacetDescriptor<CounterLevelFacet, CounterState>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelFacet_PiLevelDescriptor",
                "super(new ResourceLocation(\"example\", \"counter_level\"), CounterLevelFacet.class, CounterState.class);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelFacet_PiLevelDescriptor",
                "return new CounterLevelFacet(context);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelFacet_PiLevelProvider",
                "implements PiLevelFacetProvider"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelFacet_PiLevelProvider",
                "registry.register(new CounterLevelFacet_PiLevelDescriptor());"
        );
        assertGeneratedResourceContains(
                compilation,
                "META-INF/services/org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider",
                "example.CounterLevelFacet_PiLevelProvider"
        );
    }

    @Test
    void rejectsLevelFacetWithoutAccessibleContextConstructor() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLevelFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLevelFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor;",
                "public abstract class PiGeneratedLevelFacetDescriptor<T, S> implements PiLevelFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetRegistry {",
                "  void register(PiGeneratedLevelFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetProvider {",
                "  void register(PiLevelFacetRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLevelFacet<S> {",
                "  protected PiStateLevelFacet() {",
                "  }",
                "}"
        );
        JavaFileObject resourceLocation = JavaFileObjects.forSourceLines(
                "net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources;",
                "public record ResourceLocation(String namespace, String path) {",
                "  public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {",
                "    return new ResourceLocation(namespace, path);",
                "  }",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.MissingConstructorLevelFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacet;",
                "import org.pickaid.pibrary.api.facet.PiStateLevelFacet;",
                "@PiLevelFacet(namespace = \"example\", path = \"missing_constructor\")",
                "public final class MissingConstructorLevelFacet extends PiStateLevelFacet<CounterState> {",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, resourceLocation, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelFacet types must declare an accessible constructor accepting PiLevelFacetContext"
        );
    }

    @Test
    void rejectsLevelFacetWithInvalidNamespaceOrPath() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLevelFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLevelFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor;",
                "public abstract class PiGeneratedLevelFacetDescriptor<T, S> implements PiLevelFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetRegistry {",
                "  void register(PiGeneratedLevelFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetProvider {",
                "  void register(PiLevelFacetRegistry registry);",
                "}"
        );
        JavaFileObject resourceLocation = JavaFileObjects.forSourceLines(
                "net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources;",
                "public record ResourceLocation(String namespace, String path) {",
                "  public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {",
                "    return new ResourceLocation(namespace, path);",
                "  }",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLevelFacet<S> {",
                "  protected PiStateLevelFacet(PiLevelFacetContext context) {",
                "  }",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.InvalidLocationLevelFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacet;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLevelFacet;",
                "@PiLevelFacet(namespace = \"bad namespace\", path = \"counter level\")",
                "public final class InvalidLocationLevelFacet extends PiStateLevelFacet<CounterState> {",
                "  public InvalidLocationLevelFacet(PiLevelFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, resourceLocation, base, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelFacet requires namespace and path values to form a valid namespace:path resource location"
        );
    }

    @Test
    void rejectsLevelFacetWithParameterizedInferredStateType() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLevelFacetContext {",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiStateLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "public abstract class PiStateLevelFacet<S> {",
                "  protected PiStateLevelFacet(PiLevelFacetContext context) {",
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
                "example.ComboLevelFacet",
                "package example;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacet;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLevelFacet;",
                "@PiLevelFacet(namespace = \"example\", path = \"combo_level\")",
                "public final class ComboLevelFacet extends PiStateLevelFacet<ExampleState<String>> {",
                "  public ComboLevelFacet(PiLevelFacetContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, base, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelFacet types must resolve to a non-parameterized concrete state type"
        );
    }

    @Test
    void rejectsLevelFacetConstructorThatThrowsCheckedException() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacet",
                "package org.pickaid.pibrary.api.facet;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelFacet {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetContext",
                "package org.pickaid.pibrary.api.facet;",
                "public final class PiLevelFacetContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor",
                "package org.pickaid.pibrary.api.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelFacetDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> facetClass();",
                "  Class<S> stateType();",
                "  T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiGeneratedLevelFacetDescriptor",
                "package org.pickaid.pibrary.runtime.facet;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetDescriptor;",
                "public abstract class PiGeneratedLevelFacetDescriptor<T, S> implements PiLevelFacetDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> facetClass;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelFacetDescriptor(ResourceLocation id, Class<T> facetClass, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelFacetContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetRegistry",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetRegistry {",
                "  void register(PiGeneratedLevelFacetDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider",
                "package org.pickaid.pibrary.runtime.facet;",
                "public interface PiLevelFacetProvider {",
                "  void register(PiLevelFacetRegistry registry);",
                "}"
        );
        JavaFileObject resourceLocation = JavaFileObjects.forSourceLines(
                "net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources;",
                "public record ResourceLocation(String namespace, String path) {",
                "  public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {",
                "    return new ResourceLocation(namespace, path);",
                "  }",
                "}"
        );
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.ComboLevelFacet",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacet;",
                "import org.pickaid.pibrary.api.facet.PiLevelFacetContext;",
                "import org.pickaid.pibrary.api.facet.PiStateLevelFacet;",
                "@PiLevelFacet(namespace = \"example\", path = \"combo_level\")",
                "public final class ComboLevelFacet extends PiStateLevelFacet<CounterState> {",
                "  public ComboLevelFacet(PiLevelFacetContext context) throws IOException {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, resourceLocation, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelFacet constructors accepting PiLevelFacetContext must not throw checked exceptions because generated descriptors instantiate facets directly"
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
