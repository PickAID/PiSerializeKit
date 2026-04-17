package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.model.PiLevelServiceSpec;

public final class PiProcessorLevelGenerationSupport {
    private PiProcessorLevelGenerationSupport() {
    }

    public static void generateLevelDescriptorType(
            ProcessingEnvironment processingEnv,
            TypeElement serviceType,
            PiLevelServiceSpec spec,
            String levelServiceContext,
            String generatedLevelServiceDescriptor
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                serviceType,
                serviceType.getSimpleName() + "_PiLevelDescriptor"
        );
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), serviceType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import " + levelServiceContext + ";\n");
                writer.write("import " + generatedLevelServiceDescriptor + ";\n");
                if (needsImport(target.packageName(), spec.stateQualifiedName())) {
                    writer.write("import " + spec.stateQualifiedName() + ";\n");
                }
                writer.write("\n");
                writer.write("public final class " + target.simpleName() + " extends PiGeneratedLevelServiceDescriptor<"
                        + spec.serviceSimpleName() + ", " + spec.stateSimpleName() + "> {\n");
                writer.write("    public " + target.simpleName() + "() {\n");
                writer.write("        super(ResourceLocation.fromNamespaceAndPath(\"" + spec.namespace() + "\", \"" + spec.path() + "\"), "
                        + spec.serviceSimpleName() + ".class, " + spec.stateSimpleName() + ".class);\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public " + spec.serviceSimpleName() + " create(PiLevelServiceContext context) {\n");
                writer.write("        return new " + spec.serviceSimpleName() + "(context);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateLevelProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement serviceType,
            Set<String> levelProviderTypes,
            String levelServiceProvider,
            String levelServiceRegistry
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                serviceType,
                serviceType.getSimpleName() + "_PiLevelProvider"
        );
        String descriptorSimpleName = serviceType.getSimpleName() + "_PiLevelDescriptor";
        levelProviderTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), serviceType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import " + levelServiceProvider + ";\n");
                writer.write("import " + levelServiceRegistry + ";\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiLevelServiceProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiLevelServiceRegistry registry) {\n");
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
