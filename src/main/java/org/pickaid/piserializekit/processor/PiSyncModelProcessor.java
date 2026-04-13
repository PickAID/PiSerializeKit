package org.pickaid.piserializekit.processor;

import java.io.IOException;
import java.io.Writer;
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
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.api.schema.PiAfterDecode;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.schema.PiInferredFieldCodec;
import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.processor.migration.PiMigrationCollectionResult;
import org.pickaid.piserializekit.processor.migration.PiMigrationStepSpec;
import org.pickaid.piserializekit.processor.migration.PiMigrationValidationFailure;
import org.pickaid.piserializekit.processor.model.PiFieldAccessStrategy;
import org.pickaid.piserializekit.processor.model.PiRawKind;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;
import org.pickaid.piserializekit.processor.model.PiResolvedSerializer;
import org.pickaid.piserializekit.processor.support.PiProcessorFieldSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorMigrationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorNames;
import org.pickaid.piserializekit.processor.support.PiProcessorPacketSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSchemaSupport;
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
                    PacketSpec packetSpec = resolvePacketSpec(typeElement);
                    if (packetSpec != null && reservePacketId(typeElement, packetSpec)) {
                        generatePacketType(typeElement, packetSpec);
                        generatePacketProviderType(typeElement);
                    }
                }
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiSyncModel.class)) {
            if (element instanceof TypeElement typeElement) {
                if (!validateConcreteClassType(typeElement, "@PiSyncModel")) {
                    continue;
                }
                NoArgConstructorStatus noArgConstructorStatus = noArgConstructorStatus(typeElement, typeElement);
                if (noArgConstructorStatus == NoArgConstructorStatus.MISSING) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiSyncModel types must declare an accessible no-arg constructor",
                            typeElement
                    );
                    continue;
                }
                if (noArgConstructorStatus == NoArgConstructorStatus.THROWS_CHECKED) {
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
                SchemaIdentity schemaIdentity = resolveSchemaIdentity(typeElement);
                if (schemaIdentity == null) {
                    continue;
                }
                if (!reserveSchemaId(typeElement, schemaIdentity)) {
                    continue;
                }
                AfterDecodeSpec afterDecode = resolveAfterDecodeHook(typeElement);
                if (afterDecode == null) {
                    continue;
                }
                MigrationSpec migrations = resolveSchemaMigrations(typeElement);
                if (migrations == null) {
                    continue;
                }
                List<FieldSpec> fields = collectFields(typeElement);
                if (fields == null) {
                    continue;
                }
                generateFieldsType(typeElement, fields);
                generateSchemaType(typeElement, fields, schemaIdentity, afterDecode, migrations);
                generateSchemaProviderType(typeElement);
            }
        }
        TypeElement livingServiceAnnotation = processingEnv.getElementUtils().getTypeElement(LIVING_SERVICE_ANNOTATION);
        if (livingServiceAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(livingServiceAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiLivingService")) {
                        continue;
                    }
                    LivingServiceSpec spec = livingServiceSpec(typeElement);
                    if (spec != null) {
                        generateLivingDescriptorType(typeElement, spec);
                        generateLivingProviderType(typeElement);
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

    private NoArgConstructorStatus noArgConstructorStatus(TypeElement typeElement, Element accessSite) {
        String ownerPackage = packageName(typeElement);
        String accessPackage = packageName(accessSite);
        boolean samePackage = ownerPackage.equals(accessPackage);
        boolean hasExplicitConstructor = false;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (constructor.getParameters().isEmpty() && constructorAccessible(constructor, samePackage)) {
                return declaresCheckedExceptions(constructor)
                        ? NoArgConstructorStatus.THROWS_CHECKED
                        : NoArgConstructorStatus.ACCESSIBLE;
            }
        }
        return hasExplicitConstructor ? NoArgConstructorStatus.MISSING : NoArgConstructorStatus.ACCESSIBLE;
    }

    private MatchingConstructorStatus matchingConstructorStatus(TypeElement typeElement, String parameterType) {
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
                return declaresCheckedExceptions(constructor)
                        ? MatchingConstructorStatus.THROWS_CHECKED
                        : MatchingConstructorStatus.ACCESSIBLE;
            }
        }
        return hasExplicitConstructor ? MatchingConstructorStatus.MISSING : MatchingConstructorStatus.ACCESSIBLE;
    }

    private boolean constructorAccessible(ExecutableElement constructor, boolean samePackage) {
        Set<Modifier> modifiers = constructor.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)) {
            return false;
        }
        if (modifiers.contains(Modifier.PUBLIC)) {
            return true;
        }
        return samePackage;
    }

    private boolean declaresCheckedExceptions(ExecutableElement executable) {
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

    private String packageName(Element element) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(element);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    private List<FieldSpec> collectFields(TypeElement typeElement) {
        List<FieldSpec> fields = new ArrayList<>();
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
            FieldSpec field = resolveFieldSpec((VariableElement) enclosedElement, piField, index);
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

    private FieldSpec resolveFieldSpec(VariableElement fieldElement, PiField annotation, int index) {
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
        return new FieldSpec(
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
        NoArgConstructorStatus constructorStatus = noArgConstructorStatus(serializerTypeElement, fieldElement);
        if (constructorStatus == NoArgConstructorStatus.MISSING) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must declare an accessible no-arg constructor",
                    fieldElement
            );
            return null;
        }
        if (constructorStatus == NoArgConstructorStatus.THROWS_CHECKED) {
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

    private void generateFieldsType(TypeElement typeElement, List<FieldSpec> fields) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName() + "_PiFields";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, typeElement);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldKey;\n\n");
                writer.write("public final class " + simpleName + " {\n");
                for (FieldSpec field : fields) {
                    writer.write("    public static final PiFieldKey " + field.constantName() + " = new PiFieldKey(" + field.index() + ", \"" + field.id() + "\");\n");
                }
                if (!fields.isEmpty()) {
                    writer.write("\n");
                }
                writer.write("    private " + simpleName + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void generateSchemaType(
            TypeElement typeElement,
            List<FieldSpec> fields,
            SchemaIdentity schemaIdentity,
            AfterDecodeSpec afterDecode,
            MigrationSpec migrations
    ) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName() + "_PiSchema";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String schemaId = schemaIdentity.id();
        int version = typeElement.getAnnotation(PiSyncModel.class).version();
        int fieldCount = fields.size();
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, typeElement);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import net.minecraft.nbt.CompoundTag;\n");
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import java.util.List;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiDecodeContext;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiDirtyBits;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiDirtySet;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldDeltaMode;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaMigration;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiStateBinding;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiStateSnapshot;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSyncScope;\n\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializeServices;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializer;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializerType;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaField;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaFieldCodecs;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;\n\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.registry.PiSchemas;\n\n");
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    public static final String SCHEMA_ID = \"" + schemaId + "\";\n");
                writer.write("    public static final int VERSION = " + version + ";\n");
                writer.write("    public static final int FIELD_COUNT = " + fieldCount + ";\n");
                if (!fields.isEmpty()) {
                    writer.write("\n");
                    for (FieldSpec field : fields) {
                        writer.write("    public static final PiFieldDescriptor " + field.constantName() + " = " + descriptorExpr(typeElement, field) + ";\n");
                    }
                    writer.write("    public static final List<PiFieldDescriptor> FIELDS = List.of(" + joinConstantNames(fields) + ");\n");
                    for (FieldSpec field : fields) {
                        writer.write("    public static final PiSchemaField<" + field.valueType() + "> " + field.schemaConstantName() + " = new PiSchemaField<>(" +
                                field.constantName() + ", " + field.serializerExpression() + ");\n");
                    }
                    writer.write("    public static final List<PiSchemaField<?>> SCHEMA_FIELDS = List.of(" + joinSchemaConstantNames(fields) + ");\n\n");
                } else {
                    writer.write("    public static final List<PiFieldDescriptor> FIELDS = List.of();\n");
                    writer.write("    public static final List<PiSchemaField<?>> SCHEMA_FIELDS = List.of();\n\n");
                }
                writeMigrationConstant(writer, typeElement, migrations);
                writeBindingConstant(writer, typeElement, schemaIdentity);
                writer.write("    public static CompoundTag saveFull(" + typeElement.getSimpleName() + " self) {\n");
                writeTagWithHeader(writer, fields);
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag saveClientView(" + typeElement.getSimpleName() + " self) {\n");
                List<FieldSpec> syncedFields = syncedFields(fields);
                writeTagWithHeader(writer, syncedFields);
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag savePersisted(" + typeElement.getSimpleName() + " self) {\n");
                writeTagWithHeader(writer, persistedFields(fields));
                writer.write("    }\n\n");
                writer.write("    public static PiStateSnapshot snapshot(" + typeElement.getSimpleName() + " self) {\n");
                writer.write("        return new PiStateSnapshot(BINDING.schemaId(), VERSION, new net.minecraft.nbt.Tag[]{\n");
                for (int i = 0; i < fields.size(); i++) {
                    FieldSpec field = fields.get(i);
                    writer.write("                PiSchemaFieldCodecs.encodeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ")");
                    writer.write(i + 1 < fields.size() ? ",\n" : "\n");
                }
                writer.write("        });\n");
                writer.write("    }\n\n");
                writer.write("    public static PiDirtyBits diff(" + typeElement.getSimpleName() + " self, PiStateSnapshot snapshot) {\n");
                writer.write("        if (!snapshot.matches(BINDING)) {\n");
                writer.write("            throw new IllegalArgumentException(\"PiStateSnapshot does not match binding \" + BINDING.schemaId());\n");
                writer.write("        }\n");
                writer.write("        PiDirtyBits bits = new PiDirtyBits();\n");
                for (FieldSpec field : fields) {
                    writer.write("        net.minecraft.nbt.Tag __pi_" + field.fieldName() + "Current = PiSchemaFieldCodecs.encodeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ");\n");
                    writer.write("        if (!snapshot.sameField(" + field.index() + ", __pi_" + field.fieldName() + "Current)) {\n");
                    writer.write("            bits.mark(" + typeElement.getSimpleName() + "_PiFields." + field.constantName() + ");\n");
                    writer.write("        }\n");
                }
                writer.write("        return bits;\n");
                writer.write("    }\n\n");
                writer.write("    public static void loadFull(" + typeElement.getSimpleName() + " self, CompoundTag tag, PiDecodeContext context) {\n");
                writer.write("        CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.FULL);\n");
                writer.write("        if (__pi_payload == null) {\n");
                writer.write("            return;\n");
                writer.write("        }\n");
                for (FieldSpec field : fields) {
                    writer.write(loadStmt(field, "__pi_payload", "PiSchemaPayloadKind.FULL"));
                }
                if (afterDecode.present()) {
                    writer.write("        self." + afterDecode.methodName() + "();\n");
                }
                writer.write("    }\n\n");
                writer.write("    public static void loadPersisted(" + typeElement.getSimpleName() + " self, CompoundTag tag, PiDecodeContext context) {\n");
                writer.write("        CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.PERSISTED);\n");
                writer.write("        if (__pi_payload == null) {\n");
                writer.write("            return;\n");
                writer.write("        }\n");
                for (FieldSpec field : persistedFields(fields)) {
                    writer.write(loadStmt(field, "__pi_payload", "PiSchemaPayloadKind.PERSISTED"));
                }
                if (afterDecode.present()) {
                    writer.write("        self." + afterDecode.methodName() + "();\n");
                }
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag writeDelta(" + typeElement.getSimpleName() + " self, PiDirtySet dirtySet) {\n");
                writer.write("        CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);\n");
                for (FieldSpec field : fields) {
                    writer.write("        if (dirtySet.contains(" + typeElement.getSimpleName() + "_PiFields." + field.constantName() + ")) {\n");
                    writer.write(writeDeltaStmt(field));
                    writer.write("        }\n");
                }
                writer.write("        return tag;\n");
                writer.write("    }\n\n");
                writer.write("    public static void applyDelta(" + typeElement.getSimpleName() + " self, CompoundTag tag, PiDecodeContext context) {\n");
                writer.write("        CompoundTag __pi_payload = PiSchemaSupport.preparePayload(tag, context, BINDING, PiSchemaPayloadKind.DELTA);\n");
                writer.write("        if (__pi_payload == null) {\n");
                writer.write("            return;\n");
                writer.write("        }\n");
                for (FieldSpec field : fields) {
                    writer.write("        if (__pi_payload.contains(" + field.schemaConstantName() + ".key())) {\n");
                    writer.write(applyDeltaStmt(field, "__pi_payload", "PiSchemaPayloadKind.DELTA"));
                    writer.write("        }\n");
                }
                if (afterDecode.present()) {
                    writer.write("        self." + afterDecode.methodName() + "();\n");
                }
                writer.write("    }\n\n");
                writer.write("    private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type) {\n");
                writer.write("        return PiSerializeServices.requireSerializer(type);\n");
                writer.write("    }\n\n");
                writer.write("    private " + simpleName + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void generateSchemaProviderType(TypeElement typeElement) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName() + "_PiSchemaProvider";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        providerTypes.add(qualifiedName);
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, typeElement);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaProvider;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;\n\n");
                writer.write("public final class " + simpleName + " implements PiSchemaProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiSchemaRegistry registry) {\n");
                writer.write("        registry.register(" + typeElement.getSimpleName() + ".class, " + typeElement.getSimpleName() + "_PiSchema.BINDING);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private PacketSpec resolvePacketSpec(TypeElement typeElement) {
        if (!validateConcreteClassType(typeElement, "@PiPacket")) {
            return null;
        }
        AnnotationMirror mirror = findAnnotation(typeElement, PACKET_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationValues(mirror);
        Map<String, AnnotationValue> declaredValues = declaredAnnotationValues(mirror);
        PacketIdentity packetIdentity = resolvePacketIdentity(
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
        PacketDirectionSpec direction = resolvePacketDirection(typeElement);
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
        MigrationSpec migrations = resolvePacketMigrations(typeElement, packetVersion);
        if (migrations == null) {
            return null;
        }
        List<FieldSpec> fields = collectFields(typeElement);
        if (fields == null) {
            return null;
        }
        if (!hasCompatiblePacketConstructor(typeElement, fields)) {
            return null;
        }
        return new PacketSpec(packetIdentity.namespace(), packetIdentity.path(), packetVersion, direction, fields, migrations);
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

    private boolean reserveSchemaId(TypeElement typeElement, SchemaIdentity schemaIdentity) {
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

    private boolean reservePacketId(TypeElement typeElement, PacketSpec packetSpec) {
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

    private PacketIdentity resolvePacketIdentity(
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
            SchemaIdentity identity = resolveExplicitResourceLocation(
                    explicitId,
                    typeElement,
                    "@PiPacket.id must be a namespace:path resource location"
            );
            return identity == null ? null : new PacketIdentity(identity.namespace(), identity.path());
        }
        String namespace = resolvePacketNamespace(typeElement, explicitNamespace);
        if (namespace == null) {
            return null;
        }
        String path = resolvePacketPath(typeElement, explicitPath);
        if (path == null) {
            return null;
        }
        return new PacketIdentity(namespace, path);
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

    private PacketDirectionSpec resolvePacketDirection(TypeElement typeElement) {
        if (isAssignableTo(typeElement, SERVER_PACKET)) {
            return new PacketDirectionSpec("SERVERBOUND", "PiServerPacketContext");
        }
        if (isAssignableTo(typeElement, CLIENT_PACKET)) {
            return new PacketDirectionSpec("CLIENTBOUND", "PiClientPacketContext");
        }
        if (isAssignableTo(typeElement, BIDIRECTIONAL_PACKET)) {
            return new PacketDirectionSpec("BIDIRECTIONAL", "PiPacketContext");
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

    private boolean hasCompatiblePacketConstructor(TypeElement typeElement, List<FieldSpec> fields) {
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
                FieldSpec field = fields.get(i);
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
                if (declaresCheckedExceptions(constructor)) {
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

    private Map<String, Integer> packetConstructorTypeCounts(List<FieldSpec> fields) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FieldSpec field : fields) {
            counts.merge(field.valueType(), 1, Integer::sum);
        }
        return counts;
    }

    private String expectedPacketConstructorSignature(List<FieldSpec> fields) {
        if (fields.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec field = fields.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(field.valueType()).append(' ').append(field.fieldName());
        }
        return builder.append(')').toString();
    }

    private MigrationSpec resolvePacketMigrations(TypeElement typeElement, int targetVersion) {
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
        return new MigrationSpec(result.steps());
    }

    private void generatePacketType(TypeElement typeElement, PacketSpec packetSpec) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName() + "_PiPacket";
        String typeName = typeElement.getSimpleName().toString();
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, typeElement);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import java.util.List;\n");
                writer.write("import net.minecraft.nbt.CompoundTag;\n");
                writer.write("import net.minecraft.network.FriendlyByteBuf;\n");
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiClientPacketContext;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiPacketBinding;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiPacketCodec;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiPacketContext;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiPacketDecodeException;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiPacketDirection;\n");
                writer.write("import org.pickaid.piserializekit.api.packet.PiServerPacketContext;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiDecodeContext;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldKey;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaMigration;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializeServices;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializer;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializerType;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaFieldCodecs;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaSerializers;\n\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;\n\n");
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    public static final int VERSION = " + packetSpec.version() + ";\n");
                writer.write("    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(\"" + packetSpec.namespace() + "\", \"" + packetSpec.path() + "\");\n");
                if (!packetSpec.fields().isEmpty()) {
                    writer.write("\n");
                    for (FieldSpec field : packetSpec.fields()) {
                        writer.write("    public static final PiFieldKey " + field.constantName() + " = new PiFieldKey(" + field.index() + ", \"" + field.id() + "\");\n");
                    }
                    writer.write("    public static final List<PiFieldKey> FIELDS = List.of(" + joinConstantNames(packetSpec.fields()) + ");\n");
                    for (FieldSpec field : packetSpec.fields()) {
                        writer.write("    public static final PiSerializer<" + field.valueType() + "> " + packetSerializerConstantName(field)
                                + " = " + field.serializerExpression() + ";\n");
                    }
                } else {
                    writer.write("    public static final List<PiFieldKey> FIELDS = List.of();\n\n");
                }
                writer.write("\n");
                writeMigrationConstant(writer, typeElement, packetSpec.migrations());
                writer.write("    public static final PiPacketCodec<" + typeName + "> CODEC = new PiPacketCodec<>() {\n");
                writer.write("        @Override\n");
                writer.write("        public void write(FriendlyByteBuf buffer, " + typeName + " value) {\n");
                writer.write("            buffer.writeVarInt(VERSION);\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            " + packetSerializerConstantName(field) + ".packetCodec().write(buffer, value." + field.fieldName() + ");\n");
                }
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public " + typeName + " read(FriendlyByteBuf buffer, PiDecodeContext context) {\n");
                writer.write("            int incomingVersion = PiPacketSupport.safeRead(context, PiSchemaSupport.SCHEMA_VERSION_KEY, buffer::readVarInt, VERSION);\n");
                writer.write("            boolean legacy = incomingVersion < VERSION;\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            " + field.valueType() + " " + packetIncomingValueName(field)
                            + " = PiPacketSupport.readIncomingField(buffer, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", context, legacy, " + packetFallbackExpr(field) + ");\n");
                }
                writer.write("            if (!legacy) {\n");
                writer.write("                try {\n");
                writer.write("                    return new " + typeName + "(" + joinIncomingValueNames(packetSpec.fields()) + ");\n");
                writer.write("                } catch (RuntimeException exception) {\n");
                writer.write("                    context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet construction failed\"), true);\n");
                writer.write("                    return null;\n");
                writer.write("                }\n");
                writer.write("            }\n");
                writer.write("            CompoundTag payload = new CompoundTag();\n");
                writer.write("            payload.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, incomingVersion);\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            PiPacketSupport.writePayloadField(payload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", " + packetIncomingValueName(field) + ", context);\n");
                }
                writer.write("            CompoundTag upgradedPayload = PiPacketSupport.upgradePacketPayload(PACKET_ID.toString(), incomingVersion, VERSION, payload, MIGRATIONS, context);\n");
                writer.write("            CompoundTag resolvedPayload = upgradedPayload == null ? new CompoundTag() : upgradedPayload;\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            " + field.valueType() + " " + packetDecodedValueName(field)
                            + " = PiSchemaFieldCodecs.decode(resolvedPayload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", context, " + packetFallbackExpr(field) + ");\n");
                }
                writer.write("            try {\n");
                writer.write("                return new " + typeName + "(" + joinDecodedValueNames(packetSpec.fields()) + ");\n");
                writer.write("            } catch (RuntimeException exception) {\n");
                writer.write("                context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet construction failed\"), true);\n");
                writer.write("                return null;\n");
                writer.write("            }\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public " + typeName + " read(FriendlyByteBuf buffer) {\n");
                writer.write("            PiDecodeContext context = PiDecodeContext.strict();\n");
                writer.write("            " + typeName + " packet;\n");
                writer.write("            try {\n");
                writer.write("                packet = read(buffer, context);\n");
                writer.write("            } catch (RuntimeException exception) {\n");
                writer.write("                context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet decode failed\"), true);\n");
                writer.write("                packet = null;\n");
                writer.write("            }\n");
                writer.write("            if (context.result().hasIssues()) {\n");
                writer.write("                throw new PiPacketDecodeException(PACKET_ID, context.result());\n");
                writer.write("            }\n");
                writer.write("            return packet;\n");
                writer.write("        }\n");
                writer.write("    };\n\n");
                writer.write("    public static final PiPacketBinding<" + typeName + ", " + packetSpec.direction().contextType()
                        + "> BINDING = new PiPacketBinding<>() {\n");
                writer.write("        @Override\n");
                writer.write("        public ResourceLocation packetId() {\n");
                writer.write("            return PACKET_ID;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public int version() {\n");
                writer.write("            return VERSION;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public PiPacketDirection direction() {\n");
                writer.write("            return PiPacketDirection." + packetSpec.direction().directionName() + ";\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public Class<" + typeName + "> packetType() {\n");
                writer.write("            return " + typeName + ".class;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public List<PiFieldKey> fields() {\n");
                writer.write("            return FIELDS;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public List<PiSchemaMigration> migrations() {\n");
                writer.write("            return MIGRATIONS;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public PiPacketCodec<" + typeName + "> codec() {\n");
                writer.write("            return CODEC;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public void dispatch(" + typeName + " packet, " + packetSpec.direction().contextType() + " context) {\n");
                writer.write("            packet.handle(context);\n");
                writer.write("        }\n");
                writer.write("    };\n\n");
                writer.write("    private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type) {\n");
                writer.write("        return PiSerializeServices.requireSerializer(type);\n");
                writer.write("    }\n\n");
                writer.write("    private " + simpleName + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void generatePacketProviderType(TypeElement typeElement) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName() + "_PiPacketProvider";
        String packetSimpleName = typeElement.getSimpleName() + "_PiPacket";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        packetProviderTypes.add(qualifiedName);
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, typeElement);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import " + PACKET_PROVIDER + ";\n");
                writer.write("import " + PACKET_REGISTRY + ";\n\n");
                writer.write("public final class " + simpleName + " implements PiPacketProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiPacketRegistry registry) {\n");
                writer.write("        registry.register(" + typeElement.getSimpleName() + ".class, " + packetSimpleName + ".BINDING);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void writePacketProviderServiceFile() {
        if (packetProviderTypes.isEmpty()) {
            return;
        }
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/" + PACKET_PROVIDER
            );
            try (Writer writer = file.openWriter()) {
                for (String providerType : packetProviderTypes) {
                    writer.write(providerType);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Pi packet provider service file", e);
        }
    }

    private void writeProviderServiceFile() {
        if (providerTypes.isEmpty()) {
            return;
        }
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/org.pickaid.piserializekit.api.schema.PiSchemaProvider"
            );
            try (Writer writer = file.openWriter()) {
                for (String providerType : providerTypes) {
                    writer.write(providerType);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Pi schema provider service file", e);
        }
    }

    private void writeLivingProviderServiceFile() {
        if (livingProviderTypes.isEmpty()) {
            return;
        }
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/services/" + LIVING_SERVICE_PROVIDER
            );
            try (Writer writer = file.openWriter()) {
                for (String providerType : livingProviderTypes) {
                    writer.write(providerType);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Pi living service provider service file", e);
        }
    }

    private LivingServiceSpec livingServiceSpec(TypeElement typeElement) {
        MatchingConstructorStatus constructorStatus = matchingConstructorStatus(typeElement, LIVING_SERVICE_CONTEXT);
        if (constructorStatus == MatchingConstructorStatus.MISSING) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService types must declare an accessible constructor accepting PiLivingServiceContext",
                    typeElement
            );
            return null;
        }
        if (constructorStatus == MatchingConstructorStatus.THROWS_CHECKED) {
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
        return new LivingServiceSpec(namespace, path, serviceSimpleName, stateQualifiedName, stateSimpleName);
    }

    private void generateLivingDescriptorType(TypeElement serviceType, LivingServiceSpec spec) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(serviceType);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = serviceType.getSimpleName() + "_PiLivingDescriptor";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, serviceType);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import net.minecraft.resources.ResourceLocation;\n");
                writer.write("import net.minecraftforge.common.capabilities.Capability;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityManager;\n");
                writer.write("import net.minecraftforge.common.capabilities.CapabilityToken;\n");
                writer.write("import " + LIVING_SERVICE_CONTEXT + ";\n");
                writer.write("import " + GENERATED_LIVING_SERVICE_DESCRIPTOR + ";\n");
                if (needsImport(packageName, spec.stateQualifiedName())) {
                    writer.write("import " + spec.stateQualifiedName() + ";\n");
                }
                writer.write("\n");
                writer.write("public final class " + simpleName + " extends PiGeneratedLivingServiceDescriptor<" +
                        spec.serviceSimpleName() + ", " + spec.stateSimpleName() + "> {\n");
                writer.write("    private static final class CapabilityHolder {\n");
                writer.write("        private static final Capability<" + spec.serviceSimpleName() + "> VALUE = CapabilityManager.get(new CapabilityToken<>() {\n");
                writer.write("        });\n");
                writer.write("    }\n\n");
                writer.write("    public " + simpleName + "() {\n");
                writer.write("        super(ResourceLocation.fromNamespaceAndPath(\"" + spec.namespace() + "\", \"" + spec.path() + "\"), " +
                        spec.serviceSimpleName() + ".class, " + spec.stateSimpleName() + ".class);\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public Capability<" + spec.serviceSimpleName() + "> capability() {\n");
                writer.write("        return CapabilityHolder.VALUE;\n");
                writer.write("    }\n\n");
                writer.write("    @Override\n");
                writer.write("    public " + spec.serviceSimpleName() + " create(PiLivingServiceContext context) {\n");
                writer.write("        return new " + spec.serviceSimpleName() + "(context);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void generateLivingProviderType(TypeElement serviceType) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(serviceType);
        String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
        String simpleName = serviceType.getSimpleName() + "_PiLivingProvider";
        String descriptorSimpleName = serviceType.getSimpleName() + "_PiLivingDescriptor";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        livingProviderTypes.add(qualifiedName);
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, serviceType);
            try (Writer writer = file.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("import " + LIVING_SERVICE_PROVIDER + ";\n");
                writer.write("import " + LIVING_SERVICE_REGISTRY + ";\n\n");
                writer.write("public final class " + simpleName + " implements PiLivingServiceProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiLivingServiceRegistry registry) {\n");
                writer.write("        registry.register(new " + descriptorSimpleName + "());\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate " + qualifiedName, e);
        }
    }

    private void writeMigrationConstant(Writer writer, TypeElement typeElement, MigrationSpec migrations) throws IOException {
        String typeName = typeElement.getSimpleName().toString();
        if (!migrations.present()) {
            writer.write("    public static final List<PiSchemaMigration> MIGRATIONS = List.of();\n\n");
            return;
        }
        writer.write("    public static final List<PiSchemaMigration> MIGRATIONS = List.of(\n");
        for (int i = 0; i < migrations.steps().size(); i++) {
            PiMigrationStepSpec step = migrations.steps().get(i);
            writer.write("            PiSchemaMigration.step(" + step.fromVersion() + ", " + step.toVersion() + ", " + typeName + "::" + step.methodName() + ")");
            writer.write(i + 1 < migrations.steps().size() ? ",\n" : "\n");
        }
        writer.write("    );\n\n");
    }

    private void writeBindingConstant(Writer writer, TypeElement typeElement, SchemaIdentity schemaIdentity) throws IOException {
        String typeName = typeElement.getSimpleName().toString();
        writer.write("    public static final PiStateBinding<" + typeName + "> BINDING = new PiStateBinding<>() {\n");
        writer.write("        @Override\n");
        writer.write("        public ResourceLocation schemaId() {\n");
        writer.write("            return ResourceLocation.fromNamespaceAndPath(\"" + schemaIdentity.namespace() + "\", \"" + schemaIdentity.path() + "\");\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public String schemaIdString() {\n");
        writer.write("            return SCHEMA_ID;\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public int version() {\n");
        writer.write("            return VERSION;\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public Class<" + typeName + "> stateType() {\n");
        writer.write("            return " + typeName + ".class;\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public " + typeName + " newState() {\n");
        writer.write("            return new " + typeName + "();\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public List<PiFieldDescriptor> fields() {\n");
        writer.write("            return FIELDS;\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public List<PiSchemaMigration> migrations() {\n");
        writer.write("            return MIGRATIONS;\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public PiStateSnapshot snapshot(" + typeName + " self) {\n");
        writer.write("            return " + typeName + "_PiSchema.snapshot(self);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public PiDirtyBits diff(" + typeName + " self, PiStateSnapshot snapshot) {\n");
        writer.write("            return " + typeName + "_PiSchema.diff(self, snapshot);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public CompoundTag saveFull(" + typeName + " self) {\n");
        writer.write("            return " + typeName + "_PiSchema.saveFull(self);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public void loadFull(" + typeName + " self, CompoundTag tag, PiDecodeContext context) {\n");
        writer.write("            " + typeName + "_PiSchema.loadFull(self, tag, context);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public CompoundTag saveClientView(" + typeName + " self) {\n");
        writer.write("            return " + typeName + "_PiSchema.saveClientView(self);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public CompoundTag savePersisted(" + typeName + " self) {\n");
        writer.write("            return " + typeName + "_PiSchema.savePersisted(self);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public void loadPersisted(" + typeName + " self, CompoundTag tag, PiDecodeContext context) {\n");
        writer.write("            " + typeName + "_PiSchema.loadPersisted(self, tag, context);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public CompoundTag writeDelta(" + typeName + " self, PiDirtySet dirtySet) {\n");
        writer.write("            return " + typeName + "_PiSchema.writeDelta(self, dirtySet);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public void applyDelta(" + typeName + " self, CompoundTag tag, PiDecodeContext context) {\n");
        writer.write("            " + typeName + "_PiSchema.applyDelta(self, tag, context);\n");
        writer.write("        }\n");
        writer.write("    };\n\n");
    }

    private SchemaIdentity resolveSchemaIdentity(TypeElement typeElement) {
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

    private SchemaIdentity resolveExplicitResourceLocation(String id, Element element, String errorMessage) {
        PiResolvedResourceLocation location = PiProcessorSchemaSupport.resolveExplicitResourceLocation(id);
        if (location == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorSchemaSupport.invalidResourceLocationMessage(errorMessage, id),
                    element
            );
            return null;
        }
        return new SchemaIdentity(location.id(), location.namespace(), location.path());
    }

    private AfterDecodeSpec resolveAfterDecodeHook(TypeElement typeElement) {
        AfterDecodeSpec hook = AfterDecodeSpec.none();
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
            if (declaresCheckedExceptions(method)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiAfterDecode methods must not throw checked exceptions because generated bindings invoke them directly",
                        method
                );
                return null;
            }
            hook = new AfterDecodeSpec(method.getSimpleName().toString());
        }
        return hook;
    }

    private MigrationSpec resolveSchemaMigrations(TypeElement typeElement) {
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
        return new MigrationSpec(result.steps());
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
            if (!declaresCheckedExceptions(method)) {
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

    private String joinConstantNames(List<FieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(fields.get(i).constantName());
        }
        return builder.toString();
    }

    private String joinSchemaConstantNames(List<FieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(fields.get(i).schemaConstantName());
        }
        return builder.toString();
    }

    private List<FieldSpec> syncedFields(List<FieldSpec> fields) {
        List<FieldSpec> synced = new ArrayList<>();
        for (FieldSpec field : fields) {
            if (isClientVisibleScope(field.syncScope())) {
                synced.add(field);
            }
        }
        return synced;
    }

    private List<FieldSpec> persistedFields(List<FieldSpec> fields) {
        List<FieldSpec> persisted = new ArrayList<>();
        for (FieldSpec field : fields) {
            if (field.persist()) {
                persisted.add(field);
            }
        }
        return persisted;
    }

    private boolean isClientVisibleScope(String syncScope) {
        return switch (syncScope) {
            case "CHUNK", "TRACKING", "GLOBAL" -> true;
            case "NONE", "OWNER", "MENU" -> false;
            default -> throw new IllegalStateException("Unsupported Pi sync scope for client view generation: " + syncScope);
        };
    }

    private void writeTagWithHeader(Writer writer, List<FieldSpec> fields) throws IOException {
        writer.write("        CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);\n");
        for (FieldSpec field : fields) {
            writer.write("        PiSchemaFieldCodecs.writeField(tag, " + field.schemaConstantName() + ", self." + field.fieldName() + ");\n");
        }
        writer.write("        return tag;\n");
    }

    private String loadStmt(FieldSpec field, String payloadName, String payloadKindExpression) {
        return readStmt(field, "        ", payloadName, payloadKindExpression, false);
    }

    private String writeDeltaStmt(FieldSpec field) {
        return "            PiSchemaFieldCodecs.writeField(tag, " + field.schemaConstantName() + ", self." + field.fieldName() + ");\n";
    }

    private String applyDeltaStmt(FieldSpec field, String payloadName, String payloadKindExpression) {
        return readStmt(field, "            ", payloadName, payloadKindExpression, true);
    }

    private String readStmt(FieldSpec field, String indent, String payloadName, String payloadKindExpression, boolean deltaApply) {
        if (field.nestedSyncModel() && field.deltaMode().equals("NESTED_UPDATE")) {
            return indent + "self." + field.fieldName() + " = PiSchemaFieldCodecs.readNestedField(" + payloadName + ", " +
                    field.schemaConstantName() + ".key(), PiSchemas.require(" + field.valueType() + ".class), context, self." +
                    field.fieldName() + ", " + payloadKindExpression + ");\n";
        }
        String readCall = "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName() + ", context, " + fallbackExpr(field) + ")";
        return switch (field.accessStrategy()) {
            case ASSIGN -> indent + "self." + field.fieldName() + " = " + readCall + ";\n";
            case MUTATE_LIST -> {
                yield indent + "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName()
                        + ", context, self." + field.fieldName() + ");\n";
            }
            case MUTATE_SET -> {
                if (deltaApply && field.deltaMode().equals("MERGE_SET")) {
                    String decoded = localDecodedName(field);
                    String prefix = indent + field.valueType() + " " + decoded + " = PiSchemaFieldCodecs.readFieldOrNull(" + payloadName + ", " + field.schemaConstantName() + ", context);\n";
                    yield prefix
                            + indent + "if (" + decoded + " != null) {\n"
                            + indent + "    self." + field.fieldName() + ".addAll(" + decoded + ");\n"
                            + indent + "}\n";
                }
                yield indent + "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName()
                        + ", context, self." + field.fieldName() + ");\n";
            }
            case MUTATE_MAP -> {
                if (deltaApply && field.deltaMode().equals("MERGE_MAP")) {
                    String decoded = localDecodedName(field);
                    String prefix = indent + field.valueType() + " " + decoded + " = PiSchemaFieldCodecs.readFieldOrNull(" + payloadName + ", " + field.schemaConstantName() + ", context);\n";
                    yield prefix
                            + indent + "if (" + decoded + " != null) {\n"
                            + indent + "    self." + field.fieldName() + ".putAll(" + decoded + ");\n"
                            + indent + "}\n";
                }
                yield indent + "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName()
                        + ", context, self." + field.fieldName() + ");\n";
            }
        };
    }

    private String localDecodedName(FieldSpec field) {
        return "__pi_" + field.fieldName() + "Decoded";
    }

    private String fallbackExpr(FieldSpec field) {
        return switch (field.accessStrategy()) {
            case ASSIGN -> "self." + field.fieldName();
            case MUTATE_LIST -> "new java.util.ArrayList<>(self." + field.fieldName() + ")";
            case MUTATE_SET -> "new java.util.LinkedHashSet<>(self." + field.fieldName() + ")";
            case MUTATE_MAP -> "new java.util.LinkedHashMap<>(self." + field.fieldName() + ")";
        };
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

    private boolean needsImport(String packageName, String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String typePackage = qualifiedName.substring(0, lastDot);
        return !typePackage.equals(packageName) && !"java.lang".equals(typePackage);
    }

    private record FieldSpec(
            int index,
            String constantName,
            String schemaConstantName,
            String fieldName,
            String id,
            String valueType,
            PiRawKind rawKind,
            String syncScope,
            boolean persist,
            String serializerExpression,
            PiFieldAccessStrategy accessStrategy,
            String deltaMode,
            boolean nestedSyncModel
    ) {
    }

    private String packetSerializerConstantName(FieldSpec field) {
        return field.constantName() + "_SERIALIZER";
    }

    private String packetDecodedValueName(FieldSpec field) {
        return "__pi_" + field.fieldName();
    }

    private String packetIncomingValueName(FieldSpec field) {
        return "__pi_raw_" + field.fieldName();
    }

    private String packetFallbackExpr(FieldSpec field) {
        return switch (field.rawKind()) {
            case LIST -> "new java.util.ArrayList<>()";
            case SET -> "new java.util.LinkedHashSet<>()";
            case MAP -> "new java.util.LinkedHashMap<>()";
            case SCALAR -> switch (field.valueType()) {
                case "java.lang.Byte" -> "(byte) 0";
                case "java.lang.Short" -> "(short) 0";
                case "java.lang.Integer" -> "0";
                case "java.lang.Long" -> "0L";
                case "java.lang.Boolean" -> "false";
                case "java.lang.Float" -> "0F";
                case "java.lang.Double" -> "0D";
                case "java.lang.String" -> "\"\"";
                case "java.util.UUID" -> "new java.util.UUID(0L, 0L)";
                case "net.minecraft.resources.ResourceLocation" -> "ResourceLocation.fromNamespaceAndPath(\"minecraft\", \"empty\")";
                case "net.minecraft.nbt.CompoundTag" -> "new CompoundTag()";
                case "net.minecraft.core.BlockPos" -> "net.minecraft.core.BlockPos.ZERO";
                case "net.minecraft.world.phys.Vec3" -> "net.minecraft.world.phys.Vec3.ZERO";
                case "net.minecraft.world.item.ItemStack" -> "net.minecraft.world.item.ItemStack.EMPTY";
                default -> field.valueType().startsWith("java.util.Optional<") ? "java.util.Optional.empty()" : "null";
            };
        };
    }

    private String joinDecodedValueNames(List<FieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(packetDecodedValueName(fields.get(i)));
        }
        return builder.toString();
    }

    private String joinIncomingValueNames(List<FieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(packetIncomingValueName(fields.get(i)));
        }
        return builder.toString();
    }

    private String descriptorExpr(TypeElement typeElement, FieldSpec field) {
        String base = typeElement.getSimpleName() + "_PiFields." + field.constantName() + ", PiSyncScope." + field.syncScope() + ", " + field.persist();
        if ("REPLACE".equals(field.deltaMode())) {
            return "new PiFieldDescriptor(" + base + ")";
        }
        return "new PiFieldDescriptor(" + base + ", PiFieldDeltaMode." + field.deltaMode() + ")";
    }

    private record SchemaIdentity(String id, String namespace, String path) {
    }

    private record AfterDecodeSpec(String methodName) {
        private static AfterDecodeSpec none() {
            return new AfterDecodeSpec(null);
        }

        private boolean present() {
            return methodName != null;
        }
    }

    private record MigrationSpec(List<PiMigrationStepSpec> steps) {
        private boolean present() {
            return !steps.isEmpty();
        }
    }

    private record LivingServiceSpec(
            String namespace,
            String path,
            String serviceSimpleName,
            String stateQualifiedName,
            String stateSimpleName
    ) {
    }

    private record PacketSpec(
            String namespace,
            String path,
            int version,
            PacketDirectionSpec direction,
            List<FieldSpec> fields,
            MigrationSpec migrations
    ) {
    }

    private record PacketIdentity(String namespace, String path) {
    }

    private record PacketDirectionSpec(String directionName, String contextType) {
    }

    private enum NoArgConstructorStatus {
        ACCESSIBLE,
        MISSING,
        THROWS_CHECKED
    }

    private enum MatchingConstructorStatus {
        ACCESSIBLE,
        MISSING,
        THROWS_CHECKED
    }
}
