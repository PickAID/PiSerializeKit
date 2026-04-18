package org.pickaid.piserializekit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class PiChunkServiceProcessorTest {
    @Test
    void generatesChunkServiceDescriptorProviderAndCapabilityHolder() throws Exception {
        JavaFileObject annotation = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiChunkService",
                "package org.pickaid.pibrary.api.service;",
                "import java.lang.annotation.ElementType;",
                "import java.lang.annotation.Retention;",
                "import java.lang.annotation.RetentionPolicy;",
                "import java.lang.annotation.Target;",
                "@Retention(RetentionPolicy.SOURCE)",
                "@Target(ElementType.TYPE)",
                "public @interface PiChunkService {",
                "  String namespace();",
                "  String path();",
                "}"
        );
        JavaFileObject context = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiChunkServiceContext",
                "package org.pickaid.pibrary.api.service;",
                "public final class PiChunkServiceContext {",
                "}"
        );
        JavaFileObject descriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiChunkServiceDescriptor",
                "package org.pickaid.pibrary.api.service;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "public interface PiChunkServiceDescriptor<T, S> {",
                "  ResourceLocation id();",
                "  Class<T> serviceType();",
                "  Class<S> stateType();",
                "  Capability<T> capability();",
                "  T create(PiChunkServiceContext context);",
                "}"
        );
        JavaFileObject generatedDescriptor = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.chunk.PiGeneratedChunkServiceDescriptor",
                "package org.pickaid.pibrary.runtime.chunk;",
                "import net.minecraft.resources.ResourceLocation;",
                "import net.minecraftforge.common.capabilities.Capability;",
                "import org.pickaid.pibrary.api.service.PiChunkServiceContext;",
                "import org.pickaid.pibrary.api.service.PiChunkServiceDescriptor;",
                "public abstract class PiGeneratedChunkServiceDescriptor<T, S> implements PiChunkServiceDescriptor<T, S> {",
                "  private final ResourceLocation id;",
                "  private final Class<T> serviceType;",
                "  private final Class<S> stateType;",
                "  protected PiGeneratedChunkServiceDescriptor(ResourceLocation id, Class<T> serviceType, Class<S> stateType) {",
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
                "  public abstract T create(PiChunkServiceContext context);",
                "}"
        );
        JavaFileObject registry = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.chunk.PiChunkServiceRegistry",
                "package org.pickaid.pibrary.runtime.chunk;",
                "public interface PiChunkServiceRegistry {",
                "  void register(PiGeneratedChunkServiceDescriptor<?, ?> descriptor);",
                "}"
        );
        JavaFileObject provider = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.runtime.chunk.PiChunkServiceProvider",
                "package org.pickaid.pibrary.runtime.chunk;",
                "public interface PiChunkServiceProvider {",
                "  void register(PiChunkServiceRegistry registry);",
                "}"
        );
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "org.pickaid.pibrary.api.service.PiStateChunkService",
                "package org.pickaid.pibrary.api.service;",
                "public abstract class PiStateChunkService<S> {",
                "  protected PiStateChunkService(PiChunkServiceContext context) {",
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
        JavaFileObject capability = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.Capability",
                "package net.minecraftforge.common.capabilities;",
                "public interface Capability<T> {",
                "}"
        );
        JavaFileObject capabilityToken = JavaFileObjects.forSourceLines(
                "net.minecraftforge.common.capabilities.CapabilityToken",
                "package net.minecraftforge.common.capabilities;",
                "public abstract class CapabilityToken<T> {",
                "}"
        );
        JavaFileObject capabilityManager = JavaFileObjects.forSourceLines(
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
        JavaFileObject state = JavaFileObjects.forSourceLines(
                "example.CounterState",
                "package example;",
                "public final class CounterState {",
                "}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "example.CounterChunkService",
                "package example;",
                "import org.pickaid.pibrary.api.service.PiChunkService;",
                "import org.pickaid.pibrary.api.service.PiChunkServiceContext;",
                "import org.pickaid.pibrary.api.service.PiStateChunkService;",
                "@PiChunkService(namespace = \"example\", path = \"counter_chunk\")",
                "public final class CounterChunkService extends PiStateChunkService<CounterState> {",
                "  public CounterChunkService(PiChunkServiceContext context) {",
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
                        resourceLocation,
                        capability,
                        capabilityToken,
                        capabilityManager,
                        state,
                        service
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.CounterChunkService_PiChunkDescriptor");
        assertThat(compilation).generatedSourceFile("example.CounterChunkService_PiChunkProvider");
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkDescriptor",
                "extends PiGeneratedChunkServiceDescriptor<CounterChunkService, CounterState>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkDescriptor",
                "private static final class CapabilityHolder"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkDescriptor",
                "Capability<CounterChunkService>"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkDescriptor",
                "return new CounterChunkService(context);"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkProvider",
                "implements PiChunkServiceProvider"
        );
        assertGeneratedContains(
                compilation,
                "example.CounterChunkService_PiChunkProvider",
                "registry.register(new CounterChunkService_PiChunkDescriptor());"
        );
        assertGeneratedResourceContains(
                compilation,
                "META-INF/services/org.pickaid.pibrary.runtime.chunk.PiChunkServiceProvider",
                "example.CounterChunkService_PiChunkProvider"
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
