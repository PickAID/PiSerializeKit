package org.pickaid.piserializekit.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.api.schema.PiAfterDecode;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.schema.PiInferredFieldCodec;
import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.processor.migration.PiMigrationCollectionResult;
import org.pickaid.piserializekit.processor.migration.PiMigrationValidationFailure;
import org.pickaid.piserializekit.processor.model.PiAfterDecodeSpec;
import org.pickaid.piserializekit.processor.model.PiFieldAccessStrategy;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiLivingServiceSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiPacketDirectionSpec;
import org.pickaid.piserializekit.processor.model.PiPacketIdentity;
import org.pickaid.piserializekit.processor.model.PiPacketSpec;
import org.pickaid.piserializekit.processor.model.PiRawKind;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;
import org.pickaid.piserializekit.processor.model.PiResolvedSerializer;
import org.pickaid.piserializekit.processor.model.PiSchemaIdentity;
import org.pickaid.piserializekit.processor.support.PiProcessorExecutableSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorFieldSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorLivingGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorMigrationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorNames;
import org.pickaid.piserializekit.processor.support.PiProcessorPacketGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorPacketSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSchemaGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSchemaSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorServiceFileSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSerializerSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorTypeSupport;

@SupportedAnnotationTypes({
        "org.pickaid.piserializekit.api.schema.PiSyncModel",
        "org.pickaid.piserializekit.api.schema.PiField",
        "org.pickaid.piserializekit.api.schema.PiAfterDecode",
        "org.pickaid.piserializekit.api.schema.PiSchemaUpgrade",
        "org.pickaid.piserializekit.api.packet.PiPacket",
        "org.pickaid.piserializekit.api.packet.PiPacketUpgrade",
        "org.pickaid.pibrary.api.service.PiLivingService"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class PiSyncModelProcessor extends AbstractProcessor {
    private static final String PACKET_ANNOTATION = "org.pickaid.piserializekit.api.packet.PiPacket";
    private static final String PACKET_NAMESPACE_ANNOTATION = "org.pickaid.piserializekit.api.packet.PiPacketNamespace";
    private static final String PACKET_PROVIDER = "org.pickaid.piserializekit.api.packet.PiPacketProvider";
    private static final String PACKET_REGISTRY = "org.pickaid.piserializekit.api.packet.PiPacketRegistry";
    private static final String SERVER_PACKET = "org.pickaid.piserializekit.api.packet.PiServerPacket";
    private static final String CLIENT_PACKET = "org.pickaid.piserializekit.api.packet.PiClientPacket";
    private static final String BIDIRECTIONAL_PACKET = "org.pickaid.piserializekit.api.packet.PiBidirectionalPacket";
    private static final String LIVING_SERVICE_ANNOTATION = "org.pickaid.pibrary.api.service.PiLivingService";
    private static final String LIVING_SERVICE_CONTEXT = "org.pickaid.pibrary.api.service.PiLivingServiceContext";
    private static final String LIVING_SERVICE_DESCRIPTOR = "org.pickaid.pibrary.api.service.PiLivingServiceDescriptor";
    private static final String GENERATED_LIVING_SERVICE_DESCRIPTOR = "org.pickaid.pibrary.runtime.service.PiGeneratedLivingServiceDescriptor";
    private static final String LIVING_SERVICE_PROVIDER = "org.pickaid.pibrary.runtime.service.PiLivingServiceProvider";
    private static final String LIVING_SERVICE_REGISTRY = "org.pickaid.pibrary.runtime.service.PiLivingServiceRegistry";
    private static final String FIELD_ANNOTATION = PiField.class.getName();
    private static final String INFERRED_FIELD_CODEC = PiInferredFieldCodec.class.getName();

    private final Set<String> providerTypes = new LinkedHashSet<>();
    private final Set<String> packetProviderTypes = new LinkedHashSet<>();
    private final Set<String> livingProviderTypes = new LinkedHashSet<>();
    private final Map<String, String> schemaIds = new LinkedHashMap<>();
    private final Map<String, String> packetIds = new LinkedHashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writePacketProviderServiceFile();
            writeProviderServiceFile();
            writeLivingProviderServiceFile();
            return false;
        }
        validateAnnotationHosts(roundEnv);
        TypeElement packetAnnotation = processingEnv.getElementUtils().getTypeElement(PACKET_ANNOTATION);
        if (packetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(packetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    PiPacketSpec packetSpec = resolvePacketSpec(typeElement);
                    if (packetSpec != null && reservePacketId(typeElement, packetSpec)) {
                        PiProcessorPacketGenerationSupport.generatePacketType(processingEnv, typeElement, packetSpec);
                        PiProcessorPacketGenerationSupport.generatePacketProviderType(
                                processingEnv,
                                typeElement,
                                packetProviderTypes,
                                PACKET_PROVIDER,
                                PACKET_REGISTRY
                        );
                    }
                }
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiSyncModel.class)) {
            if (element instanceof TypeElement typeElement) {
                if (!validateConcreteClassType(typeElement, "@PiSyncModel")) {
                    continue;
                }
                PiProcessorExecutableSupport.NoArgConstructorStatus noArgConstructorStatus =
                        PiProcessorExecutableSupport.noArgConstructorStatus(processingEnv, typeElement, typeElement);
                if (noArgConstructorStatus == PiProcessorExecutableSupport.NoArgConstructorStatus.MISSING) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiSyncModel types must declare an accessible no-arg constructor",
                            typeElement
                    );
                    continue;
                }
                if (noArgConstructorStatus == PiProcessorExecutableSupport.NoArgConstructorStatus.THROWS_CHECKED) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiSyncModel no-arg constructors must not throw checked exceptions because generated bindings instantiate state hosts directly",
                            typeElement
                    );
                    continue;
                }
                if (!hasValidSchemaVersion(typeElement)) {
                    continue;
                }
                PiSchemaIdentity schemaIdentity = resolveSchemaIdentity(typeElement);
                if (schemaIdentity == null) {
                    continue;
                }
                if (!reserveSchemaId(typeElement, schemaIdentity)) {
                    continue;
                }
                PiAfterDecodeSpec afterDecode = resolveAfterDecodeHook(typeElement);
                if (afterDecode == null) {
                    continue;
                }
                PiMigrationPlan migrations = resolveSchemaMigrations(typeElement);
                if (migrations == null) {
                    continue;
                }
                List<PiFieldSpec> fields = collectFields(typeElement);
                if (fields == null) {
                    continue;
                }
                PiProcessorSchemaGenerationSupport.generateFieldsType(processingEnv, typeElement, fields);
                PiProcessorSchemaGenerationSupport.generateSchemaType(
                        processingEnv,
                        typeElement,
                        fields,
                        schemaIdentity,
                        afterDecode,
                        migrations
                );
                PiProcessorSchemaGenerationSupport.generateSchemaProviderType(processingEnv, typeElement, providerTypes);
            }
        }
        TypeElement livingServiceAnnotation = processingEnv.getElementUtils().getTypeElement(LIVING_SERVICE_ANNOTATION);
        if (livingServiceAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(livingServiceAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiLivingService")) {
                        continue;
                    }
                    PiLivingServiceSpec spec = livingServiceSpec(typeElement);
                    if (spec != null) {
                        PiProcessorLivingGenerationSupport.generateLivingDescriptorType(
                                processingEnv,
                                typeElement,
                                spec,
                                LIVING_SERVICE_CONTEXT,
                                GENERATED_LIVING_SERVICE_DESCRIPTOR
                        );
                        PiProcessorLivingGenerationSupport.generateLivingProviderType(
                                processingEnv,
                                typeElement,
                                livingProviderTypes,
                                LIVING_SERVICE_PROVIDER,
                                LIVING_SERVICE_REGISTRY
                        );
                    }
                }
            }
        }
        return false;
    }

    private void validateAnnotationHosts(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(PiField.class)) {
            if (!isFieldHostSupported(element)) {
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
            if (!isPacketMember(element)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacketUpgrade may only be declared inside @PiPacket types",
                        element
                );
            }
        }
    }

    private boolean isFieldHostSupported(Element element) {
        return isSyncModelMember(element) || isPacketMember(element);
    }

    private boolean isSyncModelMember(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && typeElement.getAnnotation(PiSyncModel.class) != null;
    }

    private boolean isPacketMember(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing instanceof TypeElement typeElement && findAnnotation(typeElement, PACKET_ANNOTATION) != null;
    }

    private List<PiFieldSpec> collectFields(TypeElement typeElement) {
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
        PiResolvedSerializer serializer = resolveSerializer(fieldElement, annotation);
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
        String deltaMode = annotation.delta().name();
        if (!isSupportedDeltaMode(
                deltaMode,
                serializer.rawKind(),
                PiProcessorTypeSupport.isNestedSyncModelType(processingEnv.getTypeUtils(), fieldElement.asType()),
                fieldElement
        )) {
            return null;
        }
        String fieldId = PiProcessorFieldSupport.resolveFieldId(fieldElement.getSimpleName().toString(), annotation.id());
        String payloadKeyError = PiProcessorFieldSupport.validatePayloadKey(fieldElement.getSimpleName().toString(), fieldId);
        if (payloadKeyError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    payloadKeyError,
                    fieldElement
            );
            return null;
        }
        boolean nestedSyncModel = PiProcessorTypeSupport.isNestedSyncModelType(
                processingEnv.getTypeUtils(),
                fieldElement.asType()
        );
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
                deltaMode,
                nestedSyncModel
        );
    }

    private boolean validateFieldAuthoringMode(VariableElement fieldElement) {
        AnnotationMirror mirror = findAnnotation(fieldElement, FIELD_ANNOTATION);
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
        Map<String, AnnotationValue> declaredValues = declaredAnnotationValues(mirror);
        String fieldAuthoringError = PiProcessorFieldSupport.validateFieldAuthoringMode(
                syncModelField,
                packetField,
                declaredValues.containsKey("id"),
                stringValue(annotationValues(mirror), "id"),
                declaredValues.containsKey("sync"),
                declaredValues.containsKey("persist"),
                fieldElement.getSimpleName().toString(),
                enumValue(annotationValues(mirror), "sync"),
                booleanValue(annotationValues(mirror), "persist"),
                enumValue(annotationValues(mirror), "delta")
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
        return enclosing instanceof TypeElement typeElement && findAnnotation(typeElement, PACKET_ANNOTATION) != null;
    }

    private boolean isSupportedDeltaMode(String deltaMode, PiRawKind rawKind, boolean nestedSyncModel, Element fieldElement) {
        String deltaModeError = PiProcessorFieldSupport.validateDeltaMode(deltaMode, rawKind.name(), nestedSyncModel);
        if (deltaModeError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, deltaModeError, fieldElement);
            return false;
        }
        return true;
    }

    private PiResolvedSerializer resolveSerializer(VariableElement fieldElement, PiField annotation) {
        TypeMirror serializerType = serializerType(fieldElement);
        if (serializerType != null && !INFERRED_FIELD_CODEC.equals(serializerType.toString())) {
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
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    serializerTypeError,
                    fieldElement
            );
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
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    providerValueTypeError,
                    fieldElement
            );
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

    private PiRawKind rawKind(TypeMirror type) {
        return PiProcessorSerializerSupport.rawKind(processingEnv.getTypeUtils(), type);
    }

    private PiPacketSpec resolvePacketSpec(TypeElement typeElement) {
        if (!validateConcreteClassType(typeElement, "@PiPacket")) {
            return null;
        }
        AnnotationMirror mirror = findAnnotation(typeElement, PACKET_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationValues(mirror);
        Map<String, AnnotationValue> declaredValues = declaredAnnotationValues(mirror);
        PiPacketIdentity packetIdentity = resolvePacketIdentity(
                typeElement,
                declaredValues.containsKey("id"),
                stringValue(values, "id"),
                declaredValues.containsKey("namespace"),
                stringValue(values, "namespace"),
                declaredValues.containsKey("path"),
                stringValue(values, "path")
        );
        if (packetIdentity == null) {
            return null;
        }
        PiPacketDirectionSpec direction = resolvePacketDirection(typeElement);
        if (direction == null) {
            return null;
        }
        Integer version = intValue(values, "version");
        int packetVersion = version == null ? 1 : version;
        if (packetVersion < 1) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiPacket.version must be >= 1",
                    typeElement
            );
            return null;
        }
        PiMigrationPlan migrations = resolvePacketMigrations(typeElement, packetVersion);
        if (migrations == null) {
            return null;
        }
        List<PiFieldSpec> fields = collectFields(typeElement);
        if (fields == null) {
            return null;
        }
        if (!hasCompatiblePacketConstructor(typeElement, fields)) {
            return null;
        }
        return new PiPacketSpec(packetIdentity.namespace(), packetIdentity.path(), packetVersion, direction, fields, migrations);
    }

    private boolean hasValidSchemaVersion(TypeElement typeElement) {
        int version = typeElement.getAnnotation(PiSyncModel.class).version();
        if (version >= 1) {
            return true;
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@PiSyncModel.version must be >= 1",
                typeElement
        );
        return false;
    }

    private boolean validateConcreteClassType(TypeElement typeElement, String annotationName) {
        if (typeElement.getKind() != ElementKind.CLASS || typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    annotationName + " types must be concrete classes",
                    typeElement
            );
            return false;
        }
        if (typeElement.getNestingKind().isNested()) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    annotationName + " types must be top-level classes because generated companions are emitted as package-level types",
                    typeElement
            );
            return false;
        }
        return true;
    }

    private boolean reserveSchemaId(TypeElement typeElement, PiSchemaIdentity schemaIdentity) {
        String owner = typeElement.getQualifiedName().toString();
        String existing = schemaIds.putIfAbsent(schemaIdentity.id(), owner);
        if (existing != null && !existing.equals(owner)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Duplicate Pi schema id " + schemaIdentity.id() + " already declared by " + existing,
                    typeElement
            );
            return false;
        }
        return true;
    }

    private boolean reservePacketId(TypeElement typeElement, PiPacketSpec packetSpec) {
        String packetId = packetSpec.namespace() + ":" + packetSpec.path();
        String owner = typeElement.getQualifiedName().toString();
        String existing = packetIds.putIfAbsent(packetId, owner);
        if (existing != null && !existing.equals(owner)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Duplicate Pi packet id " + packetId + " already declared by " + existing,
                    typeElement
            );
            return false;
        }
        return true;
    }

    private PiPacketIdentity resolvePacketIdentity(
            TypeElement typeElement,
            boolean declaredId,
            String explicitId,
            boolean declaredNamespace,
            String explicitNamespace,
            boolean declaredPath,
            String explicitPath
    ) {
        String declaredIdError = PiProcessorPacketSupport.validateDeclaredPacketId(declaredId, explicitId);
        if (declaredIdError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    declaredIdError,
                    typeElement
            );
            return null;
        }
        String declaredNamespaceError = PiProcessorPacketSupport.validateDeclaredPacketNamespace(declaredNamespace, explicitNamespace);
        if (declaredNamespaceError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    declaredNamespaceError,
                    typeElement
            );
            return null;
        }
        String declaredPathError = PiProcessorPacketSupport.validateDeclaredPacketPath(declaredPath, explicitPath);
        if (declaredPathError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    declaredPathError,
                    typeElement
            );
            return null;
        }
        boolean hasExplicitId = declaredId;
        boolean hasExplicitNamespace = declaredNamespace;
        boolean hasExplicitPath = declaredPath;
        String identityCombinationError = PiProcessorPacketSupport.validatePacketIdentityCombination(
                hasExplicitId,
                hasExplicitNamespace,
                hasExplicitPath
        );
        if (identityCombinationError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    identityCombinationError,
                    typeElement
            );
            return null;
        }
        if (hasExplicitId) {
            PiSchemaIdentity identity = resolveExplicitResourceLocation(
                    explicitId,
                    typeElement,
                    "@PiPacket.id must be a namespace:path resource location"
            );
            return identity == null ? null : new PiPacketIdentity(identity.namespace(), identity.path());
        }
        String namespace = resolvePacketNamespace(typeElement, explicitNamespace);
        if (namespace == null) {
            return null;
        }
        String path = resolvePacketPath(typeElement, explicitPath);
        if (path == null) {
            return null;
        }
        return new PiPacketIdentity(namespace, path);
    }

    private String resolvePacketNamespace(TypeElement typeElement, String explicitNamespace) {
        if (explicitNamespace != null && !explicitNamespace.isBlank()) {
            String namespaceError = PiProcessorPacketSupport.validatePacketNamespace(explicitNamespace);
            if (namespaceError != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        namespaceError,
                        typeElement
                );
                return null;
            }
            return explicitNamespace;
        }
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        AnnotationMirror packageMirror = findAnnotation(packageElement, PACKET_NAMESPACE_ANNOTATION);
        if (packageMirror == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorPacketSupport.missingPacketNamespaceMessage(),
                    typeElement
            );
            return null;
        }
        String namespace = stringValue(annotationValues(packageMirror), "value");
        if (namespace == null || namespace.isBlank() || !PiProcessorNames.isValidNamespace(namespace)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorPacketSupport.invalidPackageNamespaceMessage(namespace),
                    packageElement
            );
            return null;
        }
        return namespace;
    }

    private String resolvePacketPath(TypeElement typeElement, String explicitPath) {
        String path = PiProcessorPacketSupport.resolvePacketPath(
                explicitPath,
                typeElement.getSimpleName().toString()
        );
        String pathError = PiProcessorPacketSupport.validatePacketPath(path);
        if (pathError != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    pathError,
                    typeElement
            );
            return null;
        }
        return path;
    }

    private PiPacketDirectionSpec resolvePacketDirection(TypeElement typeElement) {
        if (isAssignableTo(typeElement, SERVER_PACKET)) {
            return new PiPacketDirectionSpec("SERVERBOUND", "PiServerPacketContext");
        }
        if (isAssignableTo(typeElement, CLIENT_PACKET)) {
            return new PiPacketDirectionSpec("CLIENTBOUND", "PiClientPacketContext");
        }
        if (isAssignableTo(typeElement, BIDIRECTIONAL_PACKET)) {
            return new PiPacketDirectionSpec("BIDIRECTIONAL", "PiPacketContext");
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@PiPacket types must extend PiServerPacket, PiClientPacket, or PiBidirectionalPacket",
                typeElement
        );
        return null;
    }

    private boolean isAssignableTo(TypeElement typeElement, String qualifiedName) {
        TypeElement target = processingEnv.getElementUtils().getTypeElement(qualifiedName);
        return target != null && processingEnv.getTypeUtils().isAssignable(typeElement.asType(), target.asType());
    }

    private boolean hasCompatiblePacketConstructor(TypeElement typeElement, List<PiFieldSpec> fields) {
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

    private Map<String, Integer> packetConstructorTypeCounts(List<PiFieldSpec> fields) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PiFieldSpec field : fields) {
            counts.merge(field.valueType(), 1, Integer::sum);
        }
        return counts;
    }

    private String expectedPacketConstructorSignature(List<PiFieldSpec> fields) {
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

    private PiMigrationPlan resolvePacketMigrations(TypeElement typeElement, int targetVersion) {
        if (!validateMigrationMethodsDoNotThrowCheckedExceptions(typeElement, PiPacketUpgrade.class, "@PiPacketUpgrade")) {
            return null;
        }
        PiMigrationCollectionResult result = PiProcessorMigrationSupport.collectMigrationSteps(
                typeElement,
                PiPacketUpgrade.class,
                "@PiPacketUpgrade",
                targetVersion,
                PiPacketUpgrade::from,
                PiPacketUpgrade::to
        );
        PiMigrationValidationFailure failure = result.failure();
        if (failure != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    failure.message(),
                    failure.element()
            );
            return null;
        }
        return new PiMigrationPlan(result.steps());
    }

    private void writePacketProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                packetProviderTypes,
                PACKET_PROVIDER,
                "Failed to generate Pi packet provider service file"
        );
    }

    private void writeProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                providerTypes,
                "org.pickaid.piserializekit.api.schema.PiSchemaProvider",
                "Failed to generate Pi schema provider service file"
        );
    }

    private void writeLivingProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                livingProviderTypes,
                LIVING_SERVICE_PROVIDER,
                "Failed to generate Pi living service provider service file"
        );
    }

    private PiLivingServiceSpec livingServiceSpec(TypeElement typeElement) {
        PiProcessorExecutableSupport.MatchingConstructorStatus constructorStatus =
                PiProcessorExecutableSupport.matchingConstructorStatus(processingEnv, typeElement, LIVING_SERVICE_CONTEXT);
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.MISSING) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService types must declare an accessible constructor accepting PiLivingServiceContext",
                    typeElement
            );
            return null;
        }
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.THROWS_CHECKED) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService constructors accepting PiLivingServiceContext must not throw checked exceptions because generated descriptors instantiate services directly",
                    typeElement
            );
            return null;
        }
        AnnotationMirror mirror = findAnnotation(typeElement, LIVING_SERVICE_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationValues(mirror);
        String namespace = stringValue(values, "namespace");
        String path = stringValue(values, "path");
        TypeMirror stateTypeMirror = typeValue(values, "state");
        if (namespace == null || path == null || stateTypeMirror == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService requires namespace, path, and state values",
                    typeElement
            );
            return null;
        }
        Element stateElement = processingEnv.getTypeUtils().asElement(stateTypeMirror);
        String stateQualifiedName = stateTypeMirror.toString();
        String stateSimpleName = stateElement instanceof TypeElement stateTypeElement
                ? stateTypeElement.getSimpleName().toString()
                : stateQualifiedName;
        String serviceSimpleName = typeElement.getSimpleName().toString();
        return new PiLivingServiceSpec(namespace, path, serviceSimpleName, stateQualifiedName, stateSimpleName);
    }

    private PiSchemaIdentity resolveSchemaIdentity(TypeElement typeElement) {
        PiSyncModel annotation = typeElement.getAnnotation(PiSyncModel.class);
        if (annotation == null) {
            return null;
        }
        return resolveExplicitResourceLocation(
                annotation.id(),
                typeElement,
                "@PiSyncModel.id must be a namespace:path resource location"
        );
    }

    private PiSchemaIdentity resolveExplicitResourceLocation(String id, Element element, String errorMessage) {
        PiResolvedResourceLocation location = PiProcessorSchemaSupport.resolveExplicitResourceLocation(id);
        if (location == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorSchemaSupport.invalidResourceLocationMessage(errorMessage, id),
                    element
            );
            return null;
        }
        return new PiSchemaIdentity(location.id(), location.namespace(), location.path());
    }

    private PiAfterDecodeSpec resolveAfterDecodeHook(TypeElement typeElement) {
        PiAfterDecodeSpec hook = PiAfterDecodeSpec.none();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getAnnotation(PiAfterDecode.class) == null) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            if (hook.present()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiAfterDecode allows only one method per @PiSyncModel type",
                        method
                );
                return null;
            }
            String afterDecodeError = PiProcessorSchemaSupport.validateAfterDecodeMethod(method);
            if (afterDecodeError != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        afterDecodeError,
                        method
                );
                return null;
            }
            if (PiProcessorExecutableSupport.declaresCheckedExceptions(processingEnv, method)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiAfterDecode methods must not throw checked exceptions because generated bindings invoke them directly",
                        method
                );
                return null;
            }
            hook = new PiAfterDecodeSpec(method.getSimpleName().toString());
        }
        return hook;
    }

    private PiMigrationPlan resolveSchemaMigrations(TypeElement typeElement) {
        int targetVersion = typeElement.getAnnotation(PiSyncModel.class).version();
        if (!validateMigrationMethodsDoNotThrowCheckedExceptions(typeElement, PiSchemaUpgrade.class, "@PiSchemaUpgrade")) {
            return null;
        }
        PiMigrationCollectionResult result = PiProcessorMigrationSupport.collectMigrationSteps(
                typeElement,
                PiSchemaUpgrade.class,
                "@PiSchemaUpgrade",
                targetVersion,
                PiSchemaUpgrade::from,
                PiSchemaUpgrade::to
        );
        PiMigrationValidationFailure failure = result.failure();
        if (failure != null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    failure.message(),
                    failure.element()
            );
            return null;
        }
        return new PiMigrationPlan(result.steps());
    }

    private <A extends java.lang.annotation.Annotation> boolean validateMigrationMethodsDoNotThrowCheckedExceptions(
            TypeElement typeElement,
            Class<A> annotationType,
            String annotationName
    ) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            if (method.getAnnotation(annotationType) == null) {
                continue;
            }
            if (!PiProcessorExecutableSupport.declaresCheckedExceptions(processingEnv, method)) {
                continue;
            }
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    annotationName + " methods must not throw checked exceptions because generated migrations invoke them directly",
                    method
            );
            return false;
        }
        return true;
    }

    private TypeMirror serializerType(Element fieldElement) {
        AnnotationMirror mirror = findAnnotation(fieldElement, FIELD_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        return typeValue(annotationValues(mirror), "serializer");
    }

    private AnnotationMirror findAnnotation(Element element, String annotationName) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationName.equals(annotationMirror.getAnnotationType().toString())) {
                return annotationMirror;
            }
        }
        return null;
    }

    private Map<String, AnnotationValue> annotationValues(AnnotationMirror mirror) {
        Map<String, AnnotationValue> values = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            values.put(entry.getKey().getSimpleName().toString(), entry.getValue());
        }
        return values;
    }

    private Map<String, AnnotationValue> declaredAnnotationValues(AnnotationMirror mirror) {
        Map<String, AnnotationValue> values = new LinkedHashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
            values.put(entry.getKey().getSimpleName().toString(), entry.getValue());
        }
        return values;
    }

    private String stringValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (String) value.getValue();
    }

    private TypeMirror typeValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (TypeMirror) value.getValue();
    }

    private Integer intValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : ((Number) value.getValue()).intValue();
    }

    private Boolean booleanValue(Map<String, AnnotationValue> values, String key) {
        AnnotationValue value = values.get(key);
        return value == null ? null : (Boolean) value.getValue();
    }

    private String enumValue(Map<String, AnnotationValue> values, String key) {
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
