package org.pickaid.piserializekit.processor.support;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

/**
 * Shared package and generated-type naming helpers for processor output.
 */
public final class PiProcessorSourceSupport {
    private PiProcessorSourceSupport() {
    }

    public static PackageElement packageElement(ProcessingEnvironment processingEnv, Element element) {
        return processingEnv.getElementUtils().getPackageOf(element);
    }

    public static String packageName(ProcessingEnvironment processingEnv, Element element) {
        PackageElement packageElement = packageElement(processingEnv, element);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    public static GeneratedSourceTarget generatedType(ProcessingEnvironment processingEnv, Element owner, String simpleName) {
        String packageName = packageName(processingEnv, owner);
        return new GeneratedSourceTarget(packageName, simpleName, qualifiedName(packageName, simpleName));
    }

    public static String qualifiedName(String packageName, String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    public record GeneratedSourceTarget(String packageName, String simpleName, String qualifiedName) {
        public String packageDeclaration() {
            return packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
        }
    }
}
