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
import javax.lang.model.type.ArrayType;
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

@SupportedAnnotationTypes({
        "org.pickaid.piserializekit.api.schema.PiSyncModel",
        "org.pickaid.piserializekit.api.packet.PiPacket",
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

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writePacketProviderServiceFile();
            writeProviderServiceFile();
            writeLivingProviderServiceFile();
            return false;
        }
        TypeElement packetAnnotation = processingEnv.getElementUtils().getTypeElement(PACKET_ANNOTATION);
        if (packetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(packetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    PacketSpec packetSpec = resolvePacketSpec(typeElement);
                    if (packetSpec != null) {
                        generatePacketType(typeElement, packetSpec);
                        generatePacketProviderType(typeElement);
                    }
                }
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PiSyncModel.class)) {
            if (element instanceof TypeElement typeElement) {
                if (!hasAccessibleNoArgConstructor(typeElement)) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiSyncModel types must declare an accessible no-arg constructor",
                            typeElement
                    );
                    continue;
                }
                SchemaIdentity schemaIdentity = resolveSchemaIdentity(typeElement);
                if (schemaIdentity == null) {
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

    private boolean hasAccessibleNoArgConstructor(TypeElement typeElement) {
        boolean hasExplicitConstructor = false;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (constructor.getParameters().isEmpty() && !constructor.getModifiers().contains(Modifier.PRIVATE)) {
                return true;
            }
        }
        return !hasExplicitConstructor;
    }

    private boolean hasAccessibleConstructor(TypeElement typeElement, String parameterType) {
        boolean hasExplicitConstructor = false;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            hasExplicitConstructor = true;
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (constructor.getParameters().size() != 1) {
                continue;
            }
            if (parameterType.equals(constructor.getParameters().get(0).asType().toString())) {
                return true;
            }
        }
        return !hasExplicitConstructor;
    }

    private List<FieldSpec> collectFields(TypeElement typeElement) {
        List<FieldSpec> fields = new ArrayList<>();
        Map<String, VariableElement> fieldIds = new LinkedHashMap<>();
        int index = 0;
        boolean valid = true;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD || enclosedElement.getModifiers().contains(Modifier.STATIC)) {
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
        ResolvedSerializer serializer = resolveSerializer(fieldElement, annotation);
        if (serializer == null) {
            return null;
        }
        FieldAccessStrategy accessStrategy = resolveAccessStrategy(fieldElement, serializer.rawKind());
        if (accessStrategy == null) {
            return null;
        }
        String deltaMode = annotation.delta().name();
        if (!isSupportedDeltaMode(deltaMode, serializer.rawKind(), fieldElement.asType(), fieldElement)) {
            return null;
        }
        return new FieldSpec(
                index,
                constantName(fieldElement.getSimpleName().toString()),
                constantName(fieldElement.getSimpleName().toString()) + "_FIELD",
                fieldElement.getSimpleName().toString(),
                annotation.id(),
                serializer.valueType(),
                serializer.rawKind(),
                annotation.sync().name(),
                annotation.persist(),
                serializer.serializerExpression(),
                accessStrategy,
                deltaMode,
                isNestedSyncModelType(fieldElement.asType())
        );
    }

    private boolean isSupportedDeltaMode(String deltaMode, RawKind rawKind, TypeMirror fieldType, Element fieldElement) {
        return switch (deltaMode) {
            case "REPLACE" -> true;
            case "NESTED_UPDATE" -> {
                if (!isNestedSyncModelType(fieldType) || rawKind != RawKind.SCALAR) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiField(delta = NESTED_UPDATE) requires a nested @PiSyncModel field",
                            fieldElement
                    );
                    yield false;
                }
                yield true;
            }
            case "MERGE_SET" -> {
                if (rawKind != RawKind.SET) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiField(delta = MERGE_SET) requires a Set field",
                            fieldElement
                    );
                    yield false;
                }
                yield true;
            }
            case "MERGE_MAP" -> {
                if (rawKind != RawKind.MAP) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@PiField(delta = MERGE_MAP) requires a Map field",
                            fieldElement
                    );
                    yield false;
                }
                yield true;
            }
            default -> {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Unsupported Pi field delta mode " + deltaMode,
                        fieldElement
                );
                yield false;
            }
        };
    }

    private ResolvedSerializer resolveSerializer(VariableElement fieldElement, PiField annotation) {
        TypeMirror serializerType = serializerType(fieldElement);
        if (serializerType != null && !INFERRED_FIELD_CODEC.equals(serializerType.toString())) {
            return resolveCustomSerializer(fieldElement, serializerType);
        }
        return resolveInferredSerializer(fieldElement.asType(), fieldElement);
    }

    private ResolvedSerializer resolveCustomSerializer(VariableElement fieldElement, TypeMirror serializerType) {
        Element serializerElement = processingEnv.getTypeUtils().asElement(serializerType);
        if (!(serializerElement instanceof TypeElement serializerTypeElement)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must reference a concrete codec provider class",
                    fieldElement
            );
            return null;
        }
        if (serializerTypeElement.getModifiers().contains(Modifier.PRIVATE)
                || serializerTypeElement.getNestingKind().isNested() && !serializerTypeElement.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must be an accessible top-level or static nested class",
                    fieldElement
            );
            return null;
        }
        if (!hasAccessibleNoArgConstructor(serializerTypeElement)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must declare an accessible no-arg constructor",
                fieldElement
            );
            return null;
        }
        TypeMirror providerValueType = resolveProviderValueType(serializerTypeElement.asType());
        if (providerValueType == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must bind PiFieldCodecProvider<T> through a concrete generic type",
                    fieldElement
            );
            return null;
        }
        if (!isConcreteProviderType(providerValueType)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer must bind PiFieldCodecProvider<T> through a concrete generic type",
                    fieldElement
            );
            return null;
        }
        TypeMirror expectedType = comparableType(fieldElement.asType());
        TypeMirror actualType = comparableType(providerValueType);
        if (!processingEnv.getTypeUtils().isSameType(expectedType, actualType)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiField.serializer value type " + actualType + " does not match field type " + expectedType,
                    fieldElement
            );
            return null;
        }
        return new ResolvedSerializer(
                boxedTypeName(expectedType),
                "new " + serializerType + "().serializer()",
                rawKind(fieldElement.asType())
        );
    }

    private TypeMirror resolveProviderValueType(TypeMirror providerType) {
        if (!(providerType instanceof DeclaredType declaredType)) {
            return null;
        }
        if (sameErasure(providerType, PiFieldCodecProvider.class.getName())) {
            return declaredType.getTypeArguments().size() == 1 ? declaredType.getTypeArguments().get(0) : null;
        }
        for (TypeMirror superType : processingEnv.getTypeUtils().directSupertypes(providerType)) {
            TypeMirror resolved = resolveProviderValueType(superType);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private boolean isConcreteProviderType(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> true;
            case ARRAY -> isConcreteProviderType(((ArrayType) type).getComponentType());
            case DECLARED -> {
                DeclaredType declaredType = (DeclaredType) type;
                boolean concrete = true;
                for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                    if (!isConcreteProviderType(typeArgument)) {
                        concrete = false;
                        break;
                    }
                }
                yield concrete;
            }
            default -> false;
        };
    }

    private TypeMirror comparableType(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return processingEnv.getTypeUtils().boxedClass((javax.lang.model.type.PrimitiveType) type).asType();
        }
        return type;
    }

    private ResolvedSerializer resolveInferredSerializer(TypeMirror type, Element fieldElement) {
        if (type.getKind() == TypeKind.BYTE || sameType(type, "java.lang.Byte")) {
            return scalarSerializer("java.lang.Byte", "requireSerializer(PiSerializers.BYTE)");
        }
        if (type.getKind() == TypeKind.SHORT || sameType(type, "java.lang.Short")) {
            return scalarSerializer("java.lang.Short", "requireSerializer(PiSerializers.SHORT)");
        }
        if (type.getKind() == TypeKind.INT || sameType(type, "java.lang.Integer")) {
            return scalarSerializer("java.lang.Integer", "requireSerializer(PiSerializers.INT)");
        }
        if (type.getKind() == TypeKind.LONG || sameType(type, "java.lang.Long")) {
            return scalarSerializer("java.lang.Long", "requireSerializer(PiSerializers.LONG)");
        }
        if (type.getKind() == TypeKind.BOOLEAN || sameType(type, "java.lang.Boolean")) {
            return scalarSerializer("java.lang.Boolean", "requireSerializer(PiSerializers.BOOLEAN)");
        }
        if (type.getKind() == TypeKind.FLOAT || sameType(type, "java.lang.Float")) {
            return scalarSerializer("java.lang.Float", "requireSerializer(PiSerializers.FLOAT)");
        }
        if (type.getKind() == TypeKind.DOUBLE || sameType(type, "java.lang.Double")) {
            return scalarSerializer("java.lang.Double", "requireSerializer(PiSerializers.DOUBLE)");
        }
        if (sameType(type, "java.lang.String")) {
            return scalarSerializer("java.lang.String", "requireSerializer(PiSerializers.STRING)");
        }
        if (sameType(type, "java.util.UUID")) {
            return scalarSerializer("java.util.UUID", "requireSerializer(PiSerializers.UUID)");
        }
        if (sameType(type, "net.minecraft.resources.ResourceLocation")) {
            return scalarSerializer("net.minecraft.resources.ResourceLocation", "requireSerializer(PiSerializers.RESOURCE_LOCATION)");
        }
        if (sameType(type, "net.minecraft.nbt.CompoundTag")) {
            return scalarSerializer("net.minecraft.nbt.CompoundTag", "requireSerializer(PiSerializers.COMPOUND_TAG)");
        }
        if (sameType(type, "net.minecraft.core.BlockPos")) {
            return scalarSerializer("net.minecraft.core.BlockPos", "requireSerializer(PiSerializers.BLOCK_POS)");
        }
        if (sameType(type, "net.minecraft.world.phys.Vec3")) {
            return scalarSerializer("net.minecraft.world.phys.Vec3", "requireSerializer(PiSerializers.VEC3)");
        }
        if (sameType(type, "net.minecraft.world.item.ItemStack")) {
            return scalarSerializer("net.minecraft.world.item.ItemStack", "requireSerializer(PiSerializers.ITEM_STACK)");
        }
        if (isNestedSyncModelType(type)) {
            return scalarSerializer(type.toString(), "PiSchemaSerializers.forState(" + type + ".class)");
        }
        if (isEnumType(type)) {
            return scalarSerializer(type.toString(), "PiSerializers.enumType(" + type + ".class)");
        }
        if (type.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            ResolvedSerializer element = resolveInferredSerializer(arrayType.getComponentType(), fieldElement);
            if (element == null) {
                return null;
            }
            return scalarSerializer(type.toString(), "PiSerializers.arrayOf(" + type + ".class, " + element.serializerExpression() + ")");
        }
        if (sameErasure(type, "java.util.Optional")) {
            TypeMirror elementType = typeArgument(type, fieldElement, 0, "Optional");
            if (elementType == null) {
                return null;
            }
            ResolvedSerializer element = resolveInferredSerializer(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new ResolvedSerializer(
                    "java.util.Optional<" + element.valueType() + ">",
                    "PiSerializers.optionalOf(" + element.serializerExpression() + ")",
                    RawKind.SCALAR
            );
        }
        if (sameErasure(type, "java.util.List")) {
            TypeMirror elementType = typeArgument(type, fieldElement, 0, "List");
            if (elementType == null) {
                return null;
            }
            ResolvedSerializer element = resolveInferredSerializer(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new ResolvedSerializer(
                    "java.util.List<" + element.valueType() + ">",
                    "PiSerializers.listOf(" + element.serializerExpression() + ")",
                    RawKind.LIST
            );
        }
        if (sameErasure(type, "java.util.Set")) {
            TypeMirror elementType = typeArgument(type, fieldElement, 0, "Set");
            if (elementType == null) {
                return null;
            }
            ResolvedSerializer element = resolveInferredSerializer(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new ResolvedSerializer(
                    "java.util.Set<" + element.valueType() + ">",
                    "PiSerializers.setOf(" + element.serializerExpression() + ")",
                    RawKind.SET
            );
        }
        if (sameErasure(type, "java.util.Map")) {
            TypeMirror keyType = typeArgument(type, fieldElement, 0, "Map");
            TypeMirror valueType = typeArgument(type, fieldElement, 1, "Map");
            if (keyType == null || valueType == null) {
                return null;
            }
            ResolvedSerializer key = resolveInferredSerializer(keyType, fieldElement);
            ResolvedSerializer value = resolveInferredSerializer(valueType, fieldElement);
            if (key == null || value == null) {
                return null;
            }
            return new ResolvedSerializer(
                    "java.util.Map<" + key.valueType() + ", " + value.valueType() + ">",
                    "PiSerializers.mapOf(" + key.serializerExpression() + ", " + value.serializerExpression() + ")",
                    RawKind.MAP
            );
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Unsupported @PiField type " + type + ". Add a local serializer override.",
                fieldElement
        );
        return null;
    }

    private ResolvedSerializer scalarSerializer(String valueType, String serializerExpression) {
        return new ResolvedSerializer(valueType, serializerExpression, RawKind.SCALAR);
    }

    private FieldAccessStrategy resolveAccessStrategy(VariableElement fieldElement, RawKind rawKind) {
        boolean isFinal = fieldElement.getModifiers().contains(Modifier.FINAL);
        if (!isFinal) {
            return FieldAccessStrategy.ASSIGN;
        }
        return switch (rawKind) {
            case LIST -> FieldAccessStrategy.MUTATE_LIST;
            case SET -> FieldAccessStrategy.MUTATE_SET;
            case MAP -> FieldAccessStrategy.MUTATE_MAP;
            case SCALAR -> {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Final @PiField values must use mutable List/Set/Map types or drop final",
                        fieldElement
                );
                yield null;
            }
        };
    }

    private boolean sameType(TypeMirror type, String qualifiedName) {
        return qualifiedName.equals(type.toString());
    }

    private boolean sameErasure(TypeMirror type, String qualifiedName) {
        return qualifiedName.equals(processingEnv.getTypeUtils().erasure(type).toString());
    }

    private boolean isEnumType(TypeMirror type) {
        Element element = processingEnv.getTypeUtils().asElement(type);
        return element != null && element.getKind() == ElementKind.ENUM;
    }

    private boolean isNestedSyncModelType(TypeMirror type) {
        Element element = processingEnv.getTypeUtils().asElement(type);
        return element instanceof TypeElement typeElement && typeElement.getAnnotation(PiSyncModel.class) != null;
    }

    private TypeMirror typeArgument(TypeMirror type, Element fieldElement, int index, String label) {
        if (!(type instanceof DeclaredType declaredType) || declaredType.getTypeArguments().size() <= index) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    label + " @PiField types must declare concrete generic arguments",
                    fieldElement
            );
            return null;
        }
        return declaredType.getTypeArguments().get(index);
    }

    private String boxedTypeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            TypeElement boxed = processingEnv.getTypeUtils().boxedClass((javax.lang.model.type.PrimitiveType) type);
            return boxed.getQualifiedName().toString();
        }
        return type.toString();
    }

    private RawKind rawKind(TypeMirror type) {
        if (sameErasure(type, "java.util.List")) {
            return RawKind.LIST;
        }
        if (sameErasure(type, "java.util.Set")) {
            return RawKind.SET;
        }
        if (sameErasure(type, "java.util.Map")) {
            return RawKind.MAP;
        }
        return RawKind.SCALAR;
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
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaField;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaFieldCodecs;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSupport;\n\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemas;\n\n");
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
                    writer.write("                PiSchemaFieldCodecs.writeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ").getSecond()");
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
                    writer.write("        net.minecraft.nbt.Tag __pi_" + field.fieldName() + "Current = PiSchemaFieldCodecs.writeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ").getSecond();\n");
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
                writer.write("        return PiSerializeServices.require().lookup(type)\n");
                writer.write("                .orElseThrow(() -> new IllegalStateException(\"Missing Pi serializer for \" + type.id() + \" / \" + type.javaType().getName()));\n");
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
        AnnotationMirror mirror = findAnnotation(typeElement, PACKET_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationValues(mirror);
        PacketIdentity packetIdentity = resolvePacketIdentity(
                typeElement,
                stringValue(values, "id"),
                stringValue(values, "namespace"),
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

    private PacketIdentity resolvePacketIdentity(
            TypeElement typeElement,
            String explicitId,
            String explicitNamespace,
            String explicitPath
    ) {
        boolean hasExplicitId = explicitId != null && !explicitId.isBlank();
        boolean hasExplicitNamespace = explicitNamespace != null && !explicitNamespace.isBlank();
        boolean hasExplicitPath = explicitPath != null && !explicitPath.isBlank();
        if (hasExplicitId) {
            if (hasExplicitNamespace || hasExplicitPath) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacket.id cannot be combined with @PiPacket.namespace or @PiPacket.path",
                        typeElement
                );
                return null;
            }
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
            if (!isValidNamespace(explicitNamespace)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacket.namespace must be a valid resource namespace",
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
                    "@PiPacket requires a namespace via @PiPacket(namespace = ...), @PiPacket(id = ...), or package-info @PiPacketNamespace(...)",
                    typeElement
            );
            return null;
        }
        String namespace = stringValue(annotationValues(packageMirror), "value");
        if (namespace == null || namespace.isBlank() || !isValidNamespace(namespace)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiPacketNamespace value must be a valid resource namespace",
                    packageElement
            );
            return null;
        }
        return namespace;
    }

    private String resolvePacketPath(TypeElement typeElement, String explicitPath) {
        String path = explicitPath == null || explicitPath.isBlank()
                ? inferPacketPath(typeElement.getSimpleName().toString())
                : explicitPath;
        if (path.isEmpty() || !isValidPath(path)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiPacket.path must resolve to a valid resource path",
                    typeElement
            );
            return null;
        }
        return path;
    }

    private String inferPacketPath(String simpleName) {
        String trimmed = simpleName.replaceFirst("(Packet|Request|Payload|ToClient|ToServer)$", "");
        if (trimmed.isEmpty()) {
            trimmed = simpleName;
        }
        return camelToSnake(trimmed);
    }

    private String camelToSnake(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current)) {
                boolean needsSeparator = i > 0
                        && (Character.isLowerCase(value.charAt(i - 1))
                        || Character.isDigit(value.charAt(i - 1))
                        || i + 1 < value.length() && Character.isLowerCase(value.charAt(i + 1)));
                if (needsSeparator) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
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
                String parameterType = boxedTypeName(constructor.getParameters().get(i).asType());
                if (!fields.get(i).valueType().equals(parameterType)) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return true;
            }
        }
        if (!hasExplicitConstructor && fields.isEmpty()) {
            return true;
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@PiPacket types must declare an accessible constructor matching @PiField order",
                typeElement
        );
        return false;
    }

    private MigrationSpec resolvePacketMigrations(TypeElement typeElement, int targetVersion) {
        List<MigrationStepSpec> steps = new ArrayList<>();
        Set<Integer> fromVersions = new LinkedHashSet<>();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getAnnotation(PiPacketUpgrade.class) == null) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            PiPacketUpgrade annotation = method.getAnnotation(PiPacketUpgrade.class);
            if (!method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.PRIVATE)
                    || method.getParameters().size() != 3
                    || !"net.minecraft.nbt.CompoundTag".equals(method.getParameters().get(0).asType().toString())
                    || !"org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind".equals(method.getParameters().get(1).asType().toString())
                    || !"org.pickaid.piserializekit.api.schema.PiDecodeContext".equals(method.getParameters().get(2).asType().toString())
                    || !"net.minecraft.nbt.CompoundTag".equals(method.getReturnType().toString())) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacketUpgrade methods must be static, non-private, return CompoundTag, and accept (CompoundTag, PiSchemaPayloadKind, PiDecodeContext)",
                        method
                );
                return null;
            }
            if (annotation.from() < 1 || annotation.to() <= annotation.from()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacketUpgrade requires to > from >= 1",
                        method
                );
                return null;
            }
            if (!fromVersions.add(annotation.from())) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiPacketUpgrade allows only one migration step per source version",
                        method
                );
                return null;
            }
            steps.add(new MigrationStepSpec(annotation.from(), annotation.to(), method.getSimpleName().toString()));
        }
        steps.sort((left, right) -> Integer.compare(left.fromVersion(), right.fromVersion()));
        if (!validateMigrationReachability(typeElement, steps, targetVersion, "@PiPacketUpgrade")) {
            return null;
        }
        return new MigrationSpec(List.copyOf(steps));
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
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldKey;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaMigration;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializeServices;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializer;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializerType;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.packet.PiPacketSupport;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaFieldCodecs;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSerializers;\n\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSupport;\n\n");
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
                writer.write("            CompoundTag payload = new CompoundTag();\n");
                writer.write("            payload.putInt(PiSchemaSupport.SCHEMA_VERSION_KEY, incomingVersion);\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            " + field.valueType() + " " + packetIncomingValueName(field)
                            + " = PiPacketSupport.readIncomingField(buffer, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", context, legacy, " + packetFallbackExpr(field) + ");\n");
                    writer.write("            PiPacketSupport.writePayloadField(payload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", " + packetIncomingValueName(field) + ", context);\n");
                }
                writer.write("            CompoundTag upgradedPayload = PiPacketSupport.upgradePacketPayload(PACKET_ID.toString(), incomingVersion, VERSION, payload, MIGRATIONS, context);\n");
                writer.write("            CompoundTag resolvedPayload = upgradedPayload == null ? new CompoundTag() : upgradedPayload;\n");
                for (FieldSpec field : packetSpec.fields()) {
                    writer.write("            " + field.valueType() + " " + packetDecodedValueName(field)
                            + " = PiSchemaFieldCodecs.decode(resolvedPayload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", context.child(" + field.constantName() + ".id()), " + packetFallbackExpr(field) + ");\n");
                }
                writer.write("            return new " + typeName + "(" + joinDecodedValueNames(packetSpec.fields()) + ");\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public " + typeName + " read(FriendlyByteBuf buffer) {\n");
                writer.write("            PiDecodeContext context = PiDecodeContext.strict();\n");
                writer.write("            " + typeName + " packet = read(buffer, context);\n");
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
                writer.write("        public PiPacketCodec<" + typeName + "> codec() {\n");
                writer.write("            return CODEC;\n");
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public void dispatch(" + typeName + " packet, " + packetSpec.direction().contextType() + " context) {\n");
                writer.write("            packet.handle(context);\n");
                writer.write("        }\n");
                writer.write("    };\n\n");
                writer.write("    private static <T> PiSerializer<T> requireSerializer(PiSerializerType<T> type) {\n");
                writer.write("        return PiSerializeServices.require().lookup(type)\n");
                writer.write("                .orElseThrow(() -> new IllegalStateException(\"Missing Pi serializer for \" + type.id() + \" / \" + type.javaType().getName()));\n");
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
        if (!hasAccessibleConstructor(typeElement, LIVING_SERVICE_CONTEXT)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService types must declare an accessible constructor accepting PiLivingServiceContext",
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
            MigrationStepSpec step = migrations.steps().get(i);
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
        int delimiter = id.indexOf(':');
        if (delimiter <= 0 || delimiter != id.lastIndexOf(':') || delimiter == id.length() - 1) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    errorMessage,
                    element
            );
            return null;
        }
        String namespace = id.substring(0, delimiter);
        String path = id.substring(delimiter + 1);
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    errorMessage,
                    element
            );
            return null;
        }
        return new SchemaIdentity(id, namespace, path);
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
            if (method.getModifiers().contains(Modifier.PRIVATE)
                    || method.getModifiers().contains(Modifier.STATIC)
                    || !method.getParameters().isEmpty()
                    || method.getReturnType().getKind() != javax.lang.model.type.TypeKind.VOID) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiAfterDecode methods must be non-static, non-private, no-arg, and return void",
                        method
                );
                return null;
            }
            hook = new AfterDecodeSpec(method.getSimpleName().toString());
        }
        return hook;
    }

    private MigrationSpec resolveSchemaMigrations(TypeElement typeElement) {
        List<MigrationStepSpec> steps = new ArrayList<>();
        Set<Integer> fromVersions = new LinkedHashSet<>();
        int targetVersion = typeElement.getAnnotation(PiSyncModel.class).version();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getAnnotation(PiSchemaUpgrade.class) == null) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            PiSchemaUpgrade annotation = method.getAnnotation(PiSchemaUpgrade.class);
            if (!method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.PRIVATE)
                    || method.getParameters().size() != 3
                    || !"net.minecraft.nbt.CompoundTag".equals(method.getParameters().get(0).asType().toString())
                    || !"org.pickaid.piserializekit.api.schema.PiSchemaPayloadKind".equals(method.getParameters().get(1).asType().toString())
                    || !"org.pickaid.piserializekit.api.schema.PiDecodeContext".equals(method.getParameters().get(2).asType().toString())
                    || !"net.minecraft.nbt.CompoundTag".equals(method.getReturnType().toString())) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiSchemaUpgrade methods must be static, non-private, return CompoundTag, and accept (CompoundTag, PiSchemaPayloadKind, PiDecodeContext)",
                        method
                );
                return null;
            }
            if (annotation.from() < 1 || annotation.to() <= annotation.from()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiSchemaUpgrade requires to > from >= 1",
                        method
                );
                return null;
            }
            if (!fromVersions.add(annotation.from())) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PiSchemaUpgrade allows only one migration step per source version",
                        method
                );
                return null;
            }
            steps.add(new MigrationStepSpec(annotation.from(), annotation.to(), method.getSimpleName().toString()));
        }
        steps.sort((left, right) -> Integer.compare(left.fromVersion(), right.fromVersion()));
        if (!validateMigrationReachability(typeElement, steps, targetVersion, "@PiSchemaUpgrade")) {
            return null;
        }
        return new MigrationSpec(List.copyOf(steps));
    }

    private boolean validateMigrationReachability(
            TypeElement typeElement,
            List<MigrationStepSpec> steps,
            int targetVersion,
            String annotationName
    ) {
        if (steps.isEmpty()) {
            return true;
        }
        Map<Integer, MigrationStepSpec> indexed = new LinkedHashMap<>();
        for (MigrationStepSpec step : steps) {
            if (step.fromVersion() >= targetVersion) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        annotationName + ".from must be lower than declared schema version " + targetVersion,
                        typeElement
                );
                return false;
            }
            if (step.toVersion() > targetVersion) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        annotationName + ".to cannot exceed declared schema version " + targetVersion,
                        typeElement
                );
                return false;
            }
            indexed.put(step.fromVersion(), step);
        }
        for (int startVersion = 1; startVersion < targetVersion; startVersion++) {
            int currentVersion = startVersion;
            while (currentVersion < targetVersion) {
                MigrationStepSpec step = indexed.get(currentVersion);
                if (step == null) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            annotationName + " chain must define a migration path from version "
                                    + startVersion + " to " + targetVersion,
                            typeElement
                    );
                    return false;
                }
                currentVersion = step.toVersion();
            }
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

    private boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            char current = namespace.charAt(i);
            if (!isLowercaseResourceChar(current) && current != '.' && current != '-') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (!isLowercaseResourceChar(current) && current != '.' && current != '-' && current != '/') {
                return false;
            }
        }
        return true;
    }

    private boolean isLowercaseResourceChar(char current) {
        return current == '_' || current >= 'a' && current <= 'z' || current >= '0' && current <= '9';
    }

    private String constantName(String simpleName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char current = simpleName.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
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
        writer.write("        return PiSchemaSupport.tagWithHeader(\n");
        writer.write("                SCHEMA_ID,\n");
        if (fields.isEmpty()) {
            writer.write("                VERSION\n");
        } else {
            writer.write("                VERSION,\n");
            for (int i = 0; i < fields.size(); i++) {
                FieldSpec field = fields.get(i);
                writer.write("                " + saveExpr(field));
                writer.write(i + 1 < fields.size() ? ",\n" : "\n");
            }
        }
        writer.write("        );\n");
    }

    private String saveExpr(FieldSpec field) {
        return "PiSchemaFieldCodecs.writeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ")";
    }

    private String loadStmt(FieldSpec field, String payloadName, String payloadKindExpression) {
        return readStmt(field, "        ", payloadName, payloadKindExpression, false);
    }

    private String writeDeltaStmt(FieldSpec field) {
        return "            tag.put(" + field.schemaConstantName() + ".key(), PiSchemaFieldCodecs.writeField(" +
                field.schemaConstantName() + ", self." + field.fieldName() + ").getSecond());\n";
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
        String readCall = "PiSchemaFieldCodecs.readField(" + payloadName + ", " + field.schemaConstantName() + ", context, " + fallbackExpr(field) + ")";
        return switch (field.accessStrategy()) {
            case ASSIGN -> indent + "self." + field.fieldName() + " = " + readCall + ";\n";
            case MUTATE_LIST -> {
                String decoded = localDecodedName(field);
                yield indent + field.valueType() + " " + decoded + " = " + readCall + ";\n"
                        + indent + "self." + field.fieldName() + ".clear();\n"
                        + indent + "self." + field.fieldName() + ".addAll(" + decoded + ");\n";
            }
            case MUTATE_SET -> {
                String decoded = localDecodedName(field);
                String prefix = indent + field.valueType() + " " + decoded + " = " + readCall + ";\n";
                if (deltaApply && field.deltaMode().equals("MERGE_SET")) {
                    yield prefix + indent + "self." + field.fieldName() + ".addAll(" + decoded + ");\n";
                }
                yield prefix
                        + indent + "self." + field.fieldName() + ".clear();\n"
                        + indent + "self." + field.fieldName() + ".addAll(" + decoded + ");\n";
            }
            case MUTATE_MAP -> {
                String decoded = localDecodedName(field);
                String prefix = indent + field.valueType() + " " + decoded + " = " + readCall + ";\n";
                if (deltaApply && field.deltaMode().equals("MERGE_MAP")) {
                    yield prefix + indent + "self." + field.fieldName() + ".putAll(" + decoded + ");\n";
                }
                yield prefix
                        + indent + "self." + field.fieldName() + ".clear();\n"
                        + indent + "self." + field.fieldName() + ".putAll(" + decoded + ");\n";
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
            RawKind rawKind,
            String syncScope,
            boolean persist,
            String serializerExpression,
            FieldAccessStrategy accessStrategy,
            String deltaMode,
            boolean nestedSyncModel
    ) {
    }

    private record ResolvedSerializer(String valueType, String serializerExpression, RawKind rawKind) {
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

    private enum RawKind {
        SCALAR,
        LIST,
        SET,
        MAP
    }

    private enum FieldAccessStrategy {
        ASSIGN,
        MUTATE_LIST,
        MUTATE_SET,
        MUTATE_MAP
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

    private record MigrationSpec(List<MigrationStepSpec> steps) {
        private boolean present() {
            return !steps.isEmpty();
        }
    }

    private record MigrationStepSpec(int fromVersion, int toVersion, String methodName) {
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
}
