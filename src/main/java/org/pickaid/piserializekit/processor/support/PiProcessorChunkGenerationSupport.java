package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.model.PiChunkServiceSpec;

public final class PiProcessorChunkGenerationSupport {
    private PiProcessorChunkGenerationSupport() {
    }

    public static void generateChunkDescriptorType(
            ProcessingEnvironment processingEnv,
            TypeElement serviceType,
            PiChunkServiceSpec spec,
            String chunkServiceContext,
            String generatedChunkServiceDescriptor
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                serviceType,
                serviceType.getSimpleName() + "_PiChunkDescriptor"
        );
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), serviceType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import net.minecraftforge.common.capabilities.Capability;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityManager;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityToken;\n");
                writer.write("import " + chunkServiceContext + ";\n");
                writer.write("import " + generatedChunkServiceDescriptor + ";\n");
                if (needsImport(target.packageName(), spec.stateQualifiedName())) {
                    writer.write("import " + spec.stateQualifiedName() + ";\n");
                }
                writer.write("\n");
                writer.write("public final class " + target.simpleName() + " extends PiGeneratedChunkServiceDescriptor<"
                        + spec.serviceSimpleName() + ", " + spec.stateSimpleName() + "> {\n");
                writer.write("    private static final class CapabilityHolder {\n");
                writer.write("        private static final Capability<" + spec.serviceSimpleName() + "> VALUE = CapabilityManager.get(new CapabilityToken<>() {\n");
                writer.write("        });\n");
                writer.write("    }\n\n");
                writer.write("    public " + target.simpleName() + "() {\n");
                writer.write("        super(ResourceLocation.fromNamespaceAndPath(\"" + spec.namespace() + "\", \"" + spec.path() + "\"), "
                        + spec.serviceSimpleName() + ".class, " + spec.stateSimpleName() + ".class);\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public Capability<" + spec.serviceSimpleName() + "> capability() {\n");
                writer.write("        return CapabilityHolder.VALUE;\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public " + spec.serviceSimpleName() + " create(PiChunkServiceContext context) {\n");
                writer.write("        return new " + spec.serviceSimpleName() + "(context);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateChunkProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement serviceType,
            Set<String> chunkProviderTypes,
            String chunkServiceProvider,
            String chunkServiceRegistry
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                serviceType,
                serviceType.getSimpleName() + "_PiChunkProvider"
        );
        String descriptorSimpleName = serviceType.getSimpleName() + "_PiChunkDescriptor";
        chunkProviderTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), serviceType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import " + chunkServiceProvider + ";\n");
                writer.write("import " + chunkServiceRegistry + ";\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiChunkServiceProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiChunkServiceRegistry registry) {\n");
                writer.write("        registry.register(new " + descriptorSimpleName + "());\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    private static boolean needsImport(String packageName, String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String typePackage = qualifiedName.substring(0, lastDot);
        return !typePackage.equals(packageName) && !"java.lang".equals(typePackage);
    }
}
