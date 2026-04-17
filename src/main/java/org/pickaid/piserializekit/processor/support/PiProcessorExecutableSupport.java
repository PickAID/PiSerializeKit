package org.pickaid.piserializekit.processor.support;

import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Shared constructor and executable validation helpers for the Pi annotation processor.
 */
public final class PiProcessorExecutableSupport {
    private PiProcessorExecutableSupport() {
    }

    public static NoArgConstructorStatus noArgConstructorStatus(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            Element accessSite
    ) {
        String ownerPackage = packageName(processingEnv, typeElement);
        String accessPackage = packageName(processingEnv, accessSite);
        boolean samePackage = ownerPackage.equals(accessPackage);
        boolean hasExplicitConstructor = false;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (constructor.getParameters().isEmpty() && constructorAccessible(constructor, samePackage)) {
                return declaresCheckedExceptions(processingEnv, constructor)
                        ? NoArgConstructorStatus.THROWS_CHECKED
                        : NoArgConstructorStatus.ACCESSIBLE;
            }
        }
        return hasExplicitConstructor ? NoArgConstructorStatus.MISSING : NoArgConstructorStatus.ACCESSIBLE;
    }

    public static MatchingConstructorStatus matchingConstructorStatus(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            String parameterType
    ) {
        boolean hasExplicitConstructor = false;
        boolean samePackage = true;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (!constructorAccessible(constructor, samePackage)) {
                continue;
            }
            if (constructor.getParameters().size() != 1) {
                continue;
            }
            if (parameterType.equals(constructor.getParameters().get(0).asType().toString())) {
                return declaresCheckedExceptions(processingEnv, constructor)
                        ? MatchingConstructorStatus.THROWS_CHECKED
                        : MatchingConstructorStatus.ACCESSIBLE;
            }
        }
        return MatchingConstructorStatus.MISSING;
    }

    public static boolean declaresCheckedExceptions(ProcessingEnvironment processingEnv, ExecutableElement executable) {
        TypeElement runtimeExceptionType = processingEnv.getElementUtils().getTypeElement(RuntimeException.class.getCanonicalName());
        TypeElement errorType = processingEnv.getElementUtils().getTypeElement(Error.class.getCanonicalName());
        if (runtimeExceptionType == null || errorType == null) {
            return !executable.getThrownTypes().isEmpty();
        }
        for (TypeMirror thrownType : executable.getThrownTypes()) {
            if (!processingEnv.getTypeUtils().isAssignable(thrownType, runtimeExceptionType.asType())
                    && !processingEnv.getTypeUtils().isAssignable(thrownType, errorType.asType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean constructorAccessible(ExecutableElement constructor, boolean samePackage) {
        Set<Modifier> modifiers = constructor.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)) {
            return false;
        }
        if (modifiers.contains(Modifier.PUBLIC)) {
            return true;
        }
        return samePackage;
    }

    private static String packageName(ProcessingEnvironment processingEnv, Element element) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(element);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    public enum NoArgConstructorStatus {
        ACCESSIBLE,
        MISSING,
        THROWS_CHECKED
    }

    public enum MatchingConstructorStatus {
        ACCESSIBLE,
        MISSING,
        THROWS_CHECKED
    }
}
