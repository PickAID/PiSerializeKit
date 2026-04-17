package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.StandardLocation;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class PiLevelServiceProcessorTest {
    @Test
    void generatesLevelServiceDescriptorAndProvider() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLevelServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiGeneratedLevelServiceDescriptor",
                "package org.pickaid.pibrary.runtime.level;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceDescriptor;",
                "public abstract class PiGeneratedLevelServiceDescriptor<T, S> implements PiLevelServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceRegistry",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceRegistry {",
                "  void register(PiGeneratedLevelServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceProvider",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceProvider {",
                "  void register(PiLevelServiceRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiStateLevelService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateLevelService<S> {",
                "  protected PiStateLevelService(PiLevelServiceContext context) {",
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
                "example.CounterLevelService",
                "package example;",
                "import org.pickaid.pibrary.api.service.PiLevelService;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateLevelService;",
                "@PiLevelService(namespace = \"example\", path = \"counter_level\")",
                "public final class CounterLevelService extends PiStateLevelService<CounterState> {",
                "  public CounterLevelService(PiLevelServiceContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, resourceLocation, state, service);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.CounterLevelService_PiLevelDescriptor");
        assertThat(compilation).generatedSourceFile("example.CounterLevelService_PiLevelProvider");
        assertGeneratedContains(
                compilation,
                "example.CounterLevelService_PiLevelDescriptor",
                "extends PiGeneratedLevelServiceDescriptor<CounterLevelService, CounterState>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelService_PiLevelDescriptor",
                "super(ResourceLocation.fromNamespaceAndPath(\"example\", \"counter_level\"), CounterLevelService.class, CounterState.class);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelService_PiLevelDescriptor",
                "return new CounterLevelService(context);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelService_PiLevelProvider",
                "implements PiLevelServiceProvider"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterLevelService_PiLevelProvider",
                "registry.register(new CounterLevelService_PiLevelDescriptor());"
        );
        assertGeneratedResourceContains(
                compilation,
                "META-INF/services/org.pickaid.pibrary.runtime.level.PiLevelServiceProvider",
                "example.CounterLevelService_PiLevelProvider"
        );
    }

    @Test
    void rejectsLevelServiceWithoutAccessibleContextConstructor() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLevelServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiGeneratedLevelServiceDescriptor",
                "package org.pickaid.pibrary.runtime.level;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceDescriptor;",
                "public abstract class PiGeneratedLevelServiceDescriptor<T, S> implements PiLevelServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceRegistry",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceRegistry {",
                "  void register(PiGeneratedLevelServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceProvider",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceProvider {",
                "  void register(PiLevelServiceRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiStateLevelService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateLevelService<S> {",
                "  protected PiStateLevelService() {",
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
                "example.MissingConstructorLevelService",
                "package example;",
                "import org.pickaid.pibrary.api.service.PiLevelService;",
                "import org.pickaid.pibrary.api.service.PiStateLevelService;",
                "@PiLevelService(namespace = \"example\", path = \"missing_constructor\")",
                "public final class MissingConstructorLevelService extends PiStateLevelService<CounterState> {",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, base, resourceLocation, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelService types must declare an accessible constructor accepting PiLevelServiceContext"
        );
    }

    @Test
    void rejectsLevelServiceWithInvalidNamespaceOrPath() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLevelServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiGeneratedLevelServiceDescriptor",
                "package org.pickaid.pibrary.runtime.level;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceDescriptor;",
                "public abstract class PiGeneratedLevelServiceDescriptor<T, S> implements PiLevelServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceRegistry",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceRegistry {",
                "  void register(PiGeneratedLevelServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceProvider",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceProvider {",
                "  void register(PiLevelServiceRegistry registry);",
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
                "org.pickaid.pibrary.api.service.PiStateLevelService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateLevelService<S> {",
                "  protected PiStateLevelService(PiLevelServiceContext context) {",
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
                "example.InvalidLocationLevelService",
                "package example;",
                "import org.pickaid.pibrary.api.service.PiLevelService;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateLevelService;",
                "@PiLevelService(namespace = \"bad namespace\", path = \"counter level\")",
                "public final class InvalidLocationLevelService extends PiStateLevelService<CounterState> {",
                "  public InvalidLocationLevelService(PiLevelServiceContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, resourceLocation, base, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelService requires namespace and path values to form a valid namespace:path resource location"
        );
    }

    @Test
    void rejectsLevelServiceWithParameterizedInferredStateType() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLevelServiceContext {",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiStateLevelService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateLevelService<S> {",
                "  protected PiStateLevelService(PiLevelServiceContext context) {",
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
                "example.ComboLevelService",
                "package example;",
                "import org.pickaid.pibrary.api.service.PiLevelService;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateLevelService;",
                "@PiLevelService(namespace = \"example\", path = \"combo_level\")",
                "public final class ComboLevelService extends PiStateLevelService<ExampleState<String>> {",
                "  public ComboLevelService(PiLevelServiceContext context) {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, base, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelService types must resolve to a non-parameterized concrete state type"
        );
    }

    @Test
    void rejectsLevelServiceConstructorThatThrowsCheckedException() {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiLevelService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiLevelServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiLevelServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "public interface PiLevelServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiGeneratedLevelServiceDescriptor",
                "package org.pickaid.pibrary.runtime.level;",
                "import net.minecraft.resources.ResourceLocation;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceDescriptor;",
                "public abstract class PiGeneratedLevelServiceDescriptor<T, S> implements PiLevelServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedLevelServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
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
                "  public abstract T create(PiLevelServiceContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceRegistry",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceRegistry {",
                "  void register(PiGeneratedLevelServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.level.PiLevelServiceProvider",
                "package org.pickaid.pibrary.runtime.level;",
                "public interface PiLevelServiceProvider {",
                "  void register(PiLevelServiceRegistry registry);",
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
                "example.ComboLevelService",
                "package example;",
                "import java.io.IOException;",
                "import org.pickaid.pibrary.api.service.PiLevelService;",
                "import org.pickaid.pibrary.api.service.PiLevelServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateLevelService;",
                "@PiLevelService(namespace = \"example\", path = \"combo_level\")",
                "public final class ComboLevelService extends PiStateLevelService<CounterState> {",
                "  public ComboLevelService(PiLevelServiceContext context) throws IOException {",
                "    super(context);",
                "  }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new PiSyncModelProcessor())
                .compile(annotation, context, descriptor, generatedDescriptor, registry, provider, resourceLocation, state, service);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "@PiLevelService constructors accepting PiLevelServiceContext must not throw checked exceptions because generated descriptors instantiate services directly"
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
