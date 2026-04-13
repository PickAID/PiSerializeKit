package org.pickaid.piserializekit.processor.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;

public final class PiProcessorPacketConstructorSupport {
    private PiProcessorPacketConstructorSupport() {
    }

    public static boolean hasCompatiblePacketConstructor(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            List<PiFieldSpec> fields
    ) {
        Map<String, Integer> fieldTypeCounts = packetConstructorTypeCounts(fields);
        boolean hasExplicitConstructor = false;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (constructor.getModifiers().contains(Modifier.PRIVATE) || constructor.getParameters().size() != fields.size()) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < fields.size(); i++) {
                PiFieldSpec field = fields.get(i);
                VariableElement parameter = constructor.getParameters().get(i);
                String parameterType = PiProcessorTypeSupport.boxedTypeName(
                        processingEnv.getTypeUtils(),
                        parameter.asType()
                );
                if (!field.valueType().equals(parameterType)) {
                    compatible = false;
                    break;
                }
                if (fieldTypeCounts.getOrDefault(field.valueType(), 0) > 1
                        && !field.fieldName().equals(parameter.getSimpleName().toString())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                if (PiProcessorExecutableSupport.declaresCheckedExceptions(processingEnv, constructor)) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiPacket constructors matching @PiField order must not throw checked exceptions because generated bindings instantiate packets directly",
                            constructor
                    );
                    return false;
                }
                return true;
            }
        }
        if (!hasExplicitConstructor && fields.isEmpty()) {
            return true;
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@PiPacket types must declare an accessible constructor matching @PiField order: "
                        + expectedPacketConstructorSignature(fields),
                typeElement
        );
        return false;
    }

    private static Map<String, Integer> packetConstructorTypeCounts(List<PiFieldSpec> fields) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PiFieldSpec field : fields) {
            counts.merge(field.valueType(), 1, Integer::sum);
        }
        return counts;
    }

    private static String expectedPacketConstructorSignature(List<PiFieldSpec> fields) {
        if (fields.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < fields.size(); i++) {
            PiFieldSpec field = fields.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(field.valueType()).append(' ').append(field.fieldName());
        }
        return builder.append(')').toString();
    }
}
