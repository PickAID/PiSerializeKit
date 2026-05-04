package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.model.PiLevelFacetSpec;

public final class PiProcessorLevelGenerationSupport {
    private PiProcessorLevelGenerationSupport() {
    }

    public static void generateLevelDescriptorType(
            ProcessingEnvironment processingEnv,
            TypeElement facetType,
            PiLevelFacetSpec spec,
            String levelFacetContext,
            String generatedLevelFacetDescriptor
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                facetType,
                facetType.getSimpleName() + "_PiLevelDescriptor"
        );
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), facetType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import " + levelFacetContext + ";\n");
                writer.write("import " + generatedLevelFacetDescriptor + ";\n");
                if (needsImport(target.packageName(), spec.stateQualifiedName())) {
                    writer.write("import " + spec.stateQualifiedName() + ";\n");
                }
                writer.write("\n");
                writer.write("public final class " + target.simpleName() + " extends PiGeneratedLevelFacetDescriptor<"
                        + spec.facetSimpleName() + ", " + spec.stateSimpleName() + "> {\n");
                writer.write("    public " + target.simpleName() + "() {\n");
                writer.write("        super(new ResourceLocation(\"" + spec.namespace() + "\", \"" + spec.path() + "\"), "
                        + spec.facetSimpleName() + ".class, " + spec.stateSimpleName() + ".class);\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public " + spec.facetSimpleName() + " create(PiLevelFacetContext context) {\n");
                writer.write("        return new " + spec.facetSimpleName() + "(context);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateLevelProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement facetType,
            Set<String> levelProviderTypes,
            String levelFacetProvider,
            String levelFacetRegistry
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                facetType,
                facetType.getSimpleName() + "_PiLevelProvider"
        );
        String descriptorSimpleName = facetType.getSimpleName() + "_PiLevelDescriptor";
        levelProviderTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), facetType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import " + levelFacetProvider + ";\n");
                writer.write("import " + levelFacetRegistry + ";\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiLevelFacetProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiLevelFacetRegistry registry) {\n");
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
