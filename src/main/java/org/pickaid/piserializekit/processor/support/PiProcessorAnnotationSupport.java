package org.pickaid.piserializekit.processor.support;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.api.schema.PiAfterDecode;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;
import org.pickaid.piserializekit.api.schema.PiSyncModel;

public final class PiProcessorAnnotationSupport {
    private final ProcessingEnvironment processingEnv;

    public PiProcessorAnnotationSupport(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void validateAnnotationHosts(RoundEnvironment roundEnv, String packetAnnotation) {
        for (Element element : roundEnv.getElementsAnnotatedWith(PiField.class)) {
            if (!isFieldHostSupported(element, packetAnnotation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiField may only be declared inside @PiSyncModel or @PiPacket types",
                        element
                );
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiAfterDecode.class)) {
            if (!isSyncModelMember(element)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiAfterDecode may only be declared inside @PiSyncModel types",
                        element
                );
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiSchemaUpgrade.class)) {
            if (!isSyncModelMember(element)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiSchemaUpgrade may only be declared inside @PiSyncModel types",
                        element
                );
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiPacketUpgrade.class)) {
            if (!isPacketMember(element, packetAnnotation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacketUpgrade may only be declared inside @PiPacket types",
                        element
                );
            }
        }
    }

    public boolean isFieldHostSupported(Element element, String packetAnnotation) {
        return isSyncModelMember(element) || isPacketMember(element, packetAnnotation);
    }

    public boolean isSyncModelMember(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && typeElement.getAnnotation(PiSyncModel.class) != null;
    }

    public boolean isPacketMember(Element element, String packetAnnotation) {
        Element enclosing = element.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && findAnnotation(typeElement, packetAnnotation) != null;
    }

    public AnnotationMirror findAnnotation(Element element, String annotationName) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationName.equals(annotationMirror.getAnnotationType().toString())) {
                return annotationMirror;
            }
        }
        return null;
    }

    public Map<String, AnnotationValue> annotationValues(AnnotationMirror mirror) {
        Map<String, AnnotationValue> values = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            values.put(entry.getKey().getSimpleName().toString(), entry.getValue());
        }
        return values;
    }

    public Map<String, AnnotationValue> declaredAnnotationValues(AnnotationMirror mirror) {
        Map<String, AnnotationValue> values = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
            values.put(entry.getKey().getSimpleName().toString(), entry.getValue());
        }
        return values;
    }

    public String stringValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (String) value.getValue();
    }

    public TypeMirror typeValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (TypeMirror) value.getValue();
    }

    public Integer intValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : ((Number) value.getValue()).intValue();
    }

    public Boolean booleanValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (Boolean) value.getValue();
    }

    public String enumValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        if (value == null) {
            return null;
        }
        Object raw = value.getValue();
        if (raw instanceof VariableElement variableElement) {
            return variableElement.getSimpleName().toString();
        }
        return raw == null ? null : raw.toString();
    }
}
