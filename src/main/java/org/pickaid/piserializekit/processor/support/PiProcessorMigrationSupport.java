package org.pickaid.piserializekit.processor.support;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.function.ToIntFunction;
import org.pickaid.piserializekit.processor.migration.PiMigrationCollectionResult;
import org.pickaid.piserializekit.processor.migration.PiMigrationStepSpec;
import org.pickaid.piserializekit.processor.migration.PiMigrationValidationFailure;

public final class PiProcessorMigrationSupport {
    private PiProcessorMigrationSupport() {
    }

    public static boolean isValidMigrationMethod(ExecutableElement method) {
        return method.getModifiers().contains(Modifier.STATIC)
                && !method.getModifiers().contains(Modifier.PRIVATE)
                && method.getParameters().size() == 3
                && "net.minecraft.nbt.CompoundTag".equals(method.getParameters().get(0).asType().toString())
                && "org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind".equals(method.getParameters().get(1).asType().toString())
                && "org.pickaid.piserializekit.api.schema.PiDecodeContext".equals(method.getParameters().get(2).asType().toString())
                && "net.minecraft.nbt.CompoundTag".equals(method.getReturnType().toString());
    }

    public static String invalidMigrationMethodMessage(String annotationName, ExecutableElement method) {
        return annotationName + " methods must match: " + expectedMigrationMethodSignature()
                + "; found: " + describeMethodSignature(method);
    }

    public static String describeMigrationSteps(List<PiMigrationStepSpec> steps) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            PiMigrationStepSpec step = steps.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(step.fromVersion()).append("->").append(step.toVersion());
        }
        return builder.toString();
    }

    private static String expectedMigrationMethodSignature() {
        return "static CompoundTag method(CompoundTag payload, PiSchemaPayloadKind kind, PiDecodeContext context)";
    }

    private static String describeMethodSignature(ExecutableElement method) {
        StringBuilder builder = new StringBuilder();
        if (method.getModifiers().contains(Modifier.STATIC)) {
            builder.append("static ");
        }
        builder.append(simpleTypeName(method.getReturnType()))
                .append(' ')
                .append(method.getSimpleName())
                .append('(');
        for (int i = 0; i < method.getParameters().size(); i++) {
            VariableElement parameter = method.getParameters().get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(simpleTypeName(parameter.asType()))
                    .append(' ')
                    .append(parameter.getSimpleName());
        }
        return builder.append(')').toString();
    }

    public static PiMigrationValidationFailure validateReachability(
            Element ownerElement,
            List<PiMigrationStepSpec> steps,
            int targetVersion,
            String annotationName,
            String declaredVersionLabel
    ) {
        if (steps.isEmpty()) {
            return null;
        }
        Map<Integer, PiMigrationStepSpec> indexed = new LinkedHashMap<>();
        for (PiMigrationStepSpec step : steps) {
            if (step.fromVersion() >= targetVersion) {
                return new PiMigrationValidationFailure(
                        step.methodElement(),
                        annotationName + " step " + step.methodName() + " declares from=" + step.fromVersion()
                                + " at or above " + declaredVersionLabel + " " + targetVersion
                );
            }
            if (step.toVersion() > targetVersion) {
                return new PiMigrationValidationFailure(
                        step.methodElement(),
                        annotationName + " step " + step.methodName() + " declares to=" + step.toVersion()
                                + " above " + declaredVersionLabel + " " + targetVersion
                );
            }
            indexed.put(step.fromVersion(), step);
        }
        for (int startVersion = 1; startVersion < targetVersion; startVersion++) {
            int currentVersion = startVersion;
            while (currentVersion < targetVersion) {
                PiMigrationStepSpec step = indexed.get(currentVersion);
                if (step == null) {
                    return new PiMigrationValidationFailure(
                            ownerElement,
                            annotationName + " chain must define a migration path from version "
                                    + startVersion + " to " + targetVersion
                                    + "; missing step from version " + currentVersion
                                    + ". Declared steps: " + describeMigrationSteps(steps)
                    );
                }
                currentVersion = step.toVersion();
            }
        }
        return null;
    }

    public static <A extends Annotation> PiMigrationCollectionResult collectMigrationSteps(
            TypeElement typeElement,
            Class<A> annotationType,
            String annotationName,
            int targetVersion,
            ToIntFunction<A> fromExtractor,
            ToIntFunction<A> toExtractor
    ) {
        List<PiMigrationStepSpec> steps = new ArrayList<>();
        Map<Integer, PiMigrationStepSpec> sourceSteps = new LinkedHashMap<>();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            A annotation = method.getAnnotation(annotationType);
            if (annotation == null) {
                continue;
            }
            if (!isValidMigrationMethod(method)) {
                return new PiMigrationCollectionResult(
                        List.of(),
                        new PiMigrationValidationFailure(
                                method,
                                invalidMigrationMethodMessage(annotationName, method)
                        )
                );
            }
            int fromVersion = fromExtractor.applyAsInt(annotation);
            int toVersion = toExtractor.applyAsInt(annotation);
            if (fromVersion < 1 || toVersion <= fromVersion) {
                return new PiMigrationCollectionResult(
                        List.of(),
                        new PiMigrationValidationFailure(method, annotationName + " requires to > from >= 1")
                );
            }
            PiMigrationStepSpec step = new PiMigrationStepSpec(fromVersion, toVersion, method.getSimpleName().toString(), method);
            PiMigrationStepSpec previous = sourceSteps.putIfAbsent(fromVersion, step);
            if (previous != null) {
                return new PiMigrationCollectionResult(
                        List.of(),
                        new PiMigrationValidationFailure(
                                method,
                                annotationName + " allows only one migration step from version " + fromVersion
                                        + "; existing method: " + previous.methodName()
                                        + ", conflicting method: " + method.getSimpleName()
                        )
                );
            }
            steps.add(step);
        }
        steps.sort((left, right) -> Integer.compare(left.fromVersion(), right.fromVersion()));
        return new PiMigrationCollectionResult(
                List.copyOf(steps),
                validateReachability(typeElement, steps, targetVersion, annotationName, declaredVersionLabel(annotationName))
        );
    }

    private static String declaredVersionLabel(String annotationName) {
        return "@PiSchemaUpgrade".equals(annotationName) ? "declared schema version" : "declared version";
    }

    private static String simpleTypeName(TypeMirror type) {
        String value = type.toString();
        int genericIndex = value.indexOf('<');
        String raw = genericIndex >= 0 ? value.substring(0, genericIndex) : value;
        int packageIndex = raw.lastIndexOf('.');
        return packageIndex >= 0 ? raw.substring(packageIndex + 1) : raw;
    }
}
