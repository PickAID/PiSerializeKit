package org.pickaid.piserializekit.processor.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.processor.model.PiFieldAccessStrategy;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiResolvedSerializer;

public final class PiProcessorFieldAuthoringSupport {
    private final ProcessingEnvironment processingEnv;
    private final PiProcessorAnnotationSupport annotations;
    private final String fieldAnnotation;
    private final String inferredFieldCodec;
    private final String packetAnnotation;

    public PiProcessorFieldAuthoringSupport(
            ProcessingEnvironment processingEnv,
            PiProcessorAnnotationSupport annotations,
            String fieldAnnotation,
            String inferredFieldCodec,
            String packetAnnotation
    ) {
        this.processingEnv = processingEnv;
        this.annotations = annotations;
        this.fieldAnnotation = fieldAnnotation;
        this.inferredFieldCodec = inferredFieldCodec;
        this.packetAnnotation = packetAnnotation;
    }

    public List<PiFieldSpec> collectFields(TypeElement typeElement) {
        List<PiFieldSpec> fields = new ArrayList<>();
        Map<String, VariableElement> fieldIds = new LinkedHashMap<>();
        int index = 0;
        boolean valid = true;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) {
                continue;
            }
            PiField piField = enclosedElement.getAnnotation(PiField.class);
            if (piField == null) {
                continue;
            }
            PiFieldSpec field = resolveFieldSpec((VariableElement) enclosedElement, piField, index);
            if (field == null) {
                valid = false;
                continue;
            }
            VariableElement duplicate = fieldIds.putIfAbsent(field.id(), (VariableElement) enclosedElement);
            if (duplicate != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Duplicate @PiField.id \"" + field.id() + "\" in " + typeElement.getSimpleName()
                                + " (already declared by field " + duplicate.getSimpleName() + ")",
                        enclosedElement
                );
                valid = false;
                continue;
            }
            fields.add(field);
            index++;
        }
        return valid ? fields : null;
    }

    private PiFieldSpec resolveFieldSpec(VariableElement fieldElement, PiField annotation, int index) {
        if (!validateFieldAuthoringMode(fieldElement)) {
            return null;
        }
        boolean packetField = isPacketField(fieldElement);
        PiResolvedSerializer serializer = resolveSerializer(fieldElement);
        if (serializer == null) {
            return null;
        }
        PiFieldAccessStrategy accessStrategy = PiProcessorFieldSupport.resolveAccessStrategy(
                fieldElement.getModifiers().contains(Modifier.FINAL),
                serializer.rawKind()
        );
        if (accessStrategy == null && !packetField) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Final @PiField values must use mutable List/Set/Map types or drop final",
                    fieldElement
            );
            return null;
        }
        boolean nestedSyncModel = PiProcessorTypeSupport.isNestedSyncModelType(
                processingEnv.getTypeUtils(),
                fieldElement.asType()
        );
        if (!isSupportedDeltaMode(annotation.delta().name(), serializer.rawKind().name(), nestedSyncModel, fieldElement)) {
            return null;
        }
        String fieldId = PiProcessorFieldSupport.resolveFieldId(fieldElement.getSimpleName().toString(), annotation.id());
        String payloadKeyError = PiProcessorFieldSupport.validatePayloadKey(fieldElement.getSimpleName().toString(), fieldId);
        if (payloadKeyError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, payloadKeyError, fieldElement);
            return null;
        }
        return new PiFieldSpec(
                index,
                PiProcessorNames.constantName(fieldElement.getSimpleName().toString()),
                PiProcessorNames.constantName(fieldElement.getSimpleName().toString()) + "_FIELD",
                fieldElement.getSimpleName().toString(),
                fieldId,
                serializer.valueType(),
                serializer.rawKind(),
                annotation.sync().name(),
                annotation.persist(),
                serializer.serializerExpression(),
                accessStrategy,
                annotation.delta().name(),
                nestedSyncModel
        );
    }

    private boolean validateFieldAuthoringMode(VariableElement fieldElement) {
        AnnotationMirror mirror = annotations.findAnnotation(fieldElement, fieldAnnotation);
        if (mirror == null) {
            return true;
        }
        boolean syncModelField = isSyncModelField(fieldElement);
        boolean packetField = isPacketField(fieldElement);
        String fieldModifierError = PiProcessorFieldSupport.validateFieldModifiers(
                fieldElement.getSimpleName().toString(),
                fieldElement.getModifiers()
        );
        if (fieldModifierError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, fieldModifierError, fieldElement);
            return false;
        }
        Map<String, AnnotationValue> values = annotations.annotationValues(mirror);
        Map<String, AnnotationValue> declaredValues = annotations.declaredAnnotationValues(mirror);
        String fieldAuthoringError = PiProcessorFieldSupport.validateFieldAuthoringMode(
                syncModelField,
                packetField,
                declaredValues.containsKey("id"),
                annotations.stringValue(values, "id"),
                declaredValues.containsKey("sync"),
                declaredValues.containsKey("persist"),
                fieldElement.getSimpleName().toString(),
                annotations.enumValue(values, "sync"),
                annotations.booleanValue(values, "persist"),
                annotations.enumValue(values, "delta")
        );
        if (fieldAuthoringError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, fieldAuthoringError, fieldElement);
            return false;
        }
        return true;
    }

    private boolean isSyncModelField(VariableElement fieldElement) {
        Element enclosing = fieldElement.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && typeElement.getAnnotation(PiSyncModel.class) != null;
    }

    private boolean isPacketField(VariableElement fieldElement) {
        Element enclosing = fieldElement.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && annotations.findAnnotation(typeElement, packetAnnotation) != null;
    }

    private boolean isSupportedDeltaMode(
            String deltaMode,
            String rawKind,
            boolean nestedSyncModel,
            Element fieldElement
    ) {
        String deltaModeError = PiProcessorFieldSupport.validateDeltaMode(deltaMode, rawKind, nestedSyncModel);
        if (deltaModeError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, deltaModeError, fieldElement);
            return false;
        }
        return true;
    }

    private PiResolvedSerializer resolveSerializer(VariableElement fieldElement) {
        TypeMirror serializerType = serializerType(fieldElement);
        if (serializerType != null && !inferredFieldCodec.equals(serializerType.toString())) {
            return resolveCustomSerializer(fieldElement, serializerType);
        }
        return resolveInferredSerializer(fieldElement.asType(), fieldElement);
    }

    private PiResolvedSerializer resolveCustomSerializer(VariableElement fieldElement, TypeMirror serializerType) {
        Element serializerElement = processingEnv.getTypeUtils().asElement(serializerType);
        if (!(serializerElement instanceof TypeElement serializerTypeElement)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must reference a concrete codec provider class",
                    fieldElement
            );
            return null;
        }
        String serializerTypeError = PiProcessorSerializerSupport.validateCustomSerializerType(serializerTypeElement);
        if (serializerTypeError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, serializerTypeError, fieldElement);
            return null;
        }
        PiProcessorExecutableSupport.NoArgConstructorStatus constructorStatus =
                PiProcessorExecutableSupport.noArgConstructorStatus(processingEnv, serializerTypeElement, fieldElement);
        if (constructorStatus == PiProcessorExecutableSupport.NoArgConstructorStatus.MISSING) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must declare an accessible no-arg constructor",
                    fieldElement
            );
            return null;
        }
        if (constructorStatus == PiProcessorExecutableSupport.NoArgConstructorStatus.THROWS_CHECKED) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must declare a no-arg constructor that does not throw checked exceptions",
                    fieldElement
            );
            return null;
        }
        TypeMirror providerValueType = PiProcessorTypeSupport.resolveProviderValueType(
                processingEnv.getTypeUtils(),
                serializerTypeElement.asType()
        );
        String providerValueTypeError = PiProcessorSerializerSupport.validateCustomSerializerValueType(
                processingEnv.getTypeUtils(),
                fieldElement.asType(),
                providerValueType
        );
        if (providerValueTypeError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, providerValueTypeError, fieldElement);
            return null;
        }
        return PiProcessorSerializerSupport.customSerializer(
                processingEnv.getTypeUtils(),
                fieldElement.asType(),
                serializerType
        );
    }

    private PiResolvedSerializer resolveInferredSerializer(TypeMirror type, Element fieldElement) {
        PiResolvedSerializer builtInScalar = PiProcessorSerializerSupport.resolveBuiltInScalarSerializer(
                processingEnv.getTypeUtils(),
                type,
                PiProcessorTypeSupport.isNestedSyncModelType(processingEnv.getTypeUtils(), type),
                PiProcessorTypeSupport.isEnumType(processingEnv.getTypeUtils(), type)
        );
        if (builtInScalar != null) {
            return builtInScalar;
        }
        PiResolvedSerializer composite = PiProcessorSerializerSupport.resolveCompositeSerializer(
                processingEnv.getTypeUtils(),
                type,
                fieldElement,
                this::resolveInferredSerializer,
                (argumentType, argumentFieldElement, index, label) -> {
                    TypeMirror typeArgument = PiProcessorTypeSupport.resolveConcreteTypeArgument(argumentType, index);
                    if (typeArgument != null) {
                        return typeArgument;
                    }
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            label + " @PiField types must declare concrete generic arguments",
                            argumentFieldElement
                    );
                    return null;
                }
        );
        if (composite != null) {
            return composite;
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Unsupported @PiField type " + type + ". Add a local serializer override.",
                fieldElement
        );
        return null;
    }

    private TypeMirror serializerType(Element fieldElement) {
        AnnotationMirror mirror = annotations.findAnnotation(fieldElement, fieldAnnotation);
        if (mirror == null) {
            return null;
        }
        return annotations.typeValue(annotations.annotationValues(mirror), "serializer");
    }
}
