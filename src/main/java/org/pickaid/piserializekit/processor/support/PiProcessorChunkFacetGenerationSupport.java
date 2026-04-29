package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.model.PiChunkFacetSpec;

public final class PiProcessorChunkFacetGenerationSupport {
    private PiProcessorChunkFacetGenerationSupport() {
    }

    public static void generateChunkDescriptorType(
            ProcessingEnvironment processingEnv,
            TypeElement facetType,
            PiChunkFacetSpec spec,
            String chunkFacetContext,
            String generatedChunkFacetDescriptor
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                facetType,
                facetType.getSimpleName() + "_PiChunkDescriptor"
        );
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), facetType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import net.minecraftforge.common.capabilities.Capability;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityManager;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityToken;\n");
                writer.write("import " + chunkFacetContext + ";\n");
                writer.write("import " + generatedChunkFacetDescriptor + ";\n");
                if (needsImport(target.packageName(), spec.stateQualifiedName())) {
                    writer.write("import " + spec.stateQualifiedName() + ";\n");
                }
                writer.write("\n");
                writer.write("public final class " + target.simpleName() + " extends PiGeneratedChunkFacetDescriptor<"
                        + spec.facetSimpleName() + ", " + spec.stateSimpleName() + "> {\n");
                writer.write("    private static final class CapabilityHolder {\n");
                writer.write("        private static final Capability<" + spec.facetSimpleName() + "> VALUE = CapabilityManager.get(new CapabilityToken<>() {\n");
                writer.write("        });\n");
                writer.write("    }\n\n");
                writer.write("    public " + target.simpleName() + "() {\n");
                writer.write("        super(ResourceLocation.fromNamespaceAndPath(\"" + spec.namespace() + "\", \"" + spec.path() + "\"), "
                        + spec.facetSimpleName() + ".class, " + spec.stateSimpleName() + ".class);\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public Capability<" + spec.facetSimpleName() + "> capability() {\n");
                writer.write("        return CapabilityHolder.VALUE;\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public " + spec.facetSimpleName() + " create(PiChunkFacetContext context) {\n");
                writer.write("        return new " + spec.facetSimpleName() + "(context);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateChunkProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement facetType,
            Set<String> chunkProviderTypes,
            String chunkFacetProvider,
            String chunkFacetRegistry
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                facetType,
                facetType.getSimpleName() + "_PiChunkProvider"
        );
        String descriptorSimpleName = facetType.getSimpleName() + "_PiChunkDescriptor";
        chunkProviderTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), facetType);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import " + chunkFacetProvider + ";\n");
                writer.write("import " + chunkFacetRegistry + ";\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiChunkFacetProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiChunkFacetRegistry registry) {\n");
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
