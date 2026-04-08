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
import org.pickaid.piserializekit.api.schema.PiAfterDecode;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.schema.PiInferredFieldCodec;
import org.pickaid.piserializekit.api.schema.PiSyncModel;

@SupportedAnnotationTypes({
        "org.pickaid.piserializekit.api.schema.PiSyncModel",
        "org.pickaid.pibrary.api.service.PiLivingService"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class PiSyncModelProcessor extends AbstractProcessor {
    private static final String LIVING_SERVICE_ANNOTATION = "org.pickaid.pibrary.api.service.PiLivingService";
    private static final String LIVING_SERVICE_CONTEXT = "org.pickaid.pibrary.api.service.PiLivingServiceContext";
    private static final String LIVING_SERVICE_DESCRIPTOR = "org.pickaid.pibrary.api.service.PiLivingServiceDescriptor";
    private static final String GENERATED_LIVING_SERVICE_DESCRIPTOR = "org.pickaid.pibrary.runtime.service.PiGeneratedLivingServiceDescriptor";
    private static final String LIVING_SERVICE_PROVIDER = "org.pickaid.pibrary.runtime.service.PiLivingServiceProvider";
    private static final String LIVING_SERVICE_REGISTRY = "org.pickaid.pibrary.runtime.service.PiLivingServiceRegistry";
    private static final String FIELD_ANNOTATION = PiField.class.getName();
    private static final String INFERRED_FIELD_CODEC = PiInferredFieldCodec.class.getName();

    private final Set<String> providerTypes = new LinkedHashSet<>();
    private final Set<String> livingProviderTypes = new LinkedHashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writeProviderServiceFile();
            writeLivingProviderServiceFile();
            return false;
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
                List<FieldSpec> fields = collectFields(typeElement);
                if (fields == null) {
                    continue;
                }
                generateFieldsType(typeElement, fields);
                generateSchemaType(typeElement, fields, schemaIdentity, afterDecode);
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
        return new FieldSpec(
                index,
                constantName(fieldElement.getSimpleName().toString()),
                constantName(fieldElement.getSimpleName().toString()) + "_FIELD",
                fieldElement.getSimpleName().toString(),
                annotation.id(),
                serializer.valueType(),
                annotation.sync().name(),
                annotation.persist(),
                serializer.serializerExpression(),
                accessStrategy
        );
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
            AfterDecodeSpec afterDecode
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
                writer.write("import org.pickaid.piserializekit.api.schema.PiDirtySet;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiStateBinding;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSyncScope;\n\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializeServices;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializer;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializerType;\n");
                writer.write("import org.pickaid.piserializekit.api.service.PiSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaField;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaFieldCodecs;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSerializers;\n");
                writer.write("import org.pickaid.piserializekit.runtime.schema.PiSchemaSupport;\n\n");
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    public static final String SCHEMA_ID = \"" + schemaId + "\";\n");
                writer.write("    public static final int VERSION = " + version + ";\n");
                writer.write("    public static final int FIELD_COUNT = " + fieldCount + ";\n");
                if (!fields.isEmpty()) {
                    writer.write("\n");
                    for (FieldSpec field : fields) {
                        writer.write("    public static final PiFieldDescriptor " + field.constantName() + " = new PiFieldDescriptor(" +
                                typeElement.getSimpleName() + "_PiFields." + field.constantName() + ", PiSyncScope." + field.syncScope() + ", " + field.persist() + ");\n");
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
                writeBindingConstant(writer, typeElement, schemaIdentity);
                writer.write("    public static CompoundTag saveFull(" + typeElement.getSimpleName() + " self) {\n");
                writeTagWithHeader(writer, fields);
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag saveClientView(" + typeElement.getSimpleName() + " self) {\n");
                List<FieldSpec> syncedFields = syncedFields(fields);
                writeTagWithHeader(writer, syncedFields);
                writer.write("    }\n\n");
                writer.write("    public static void loadFull(" + typeElement.getSimpleName() + " self, CompoundTag tag, PiDecodeContext context) {\n");
                writer.write("        if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {\n");
                writer.write("            return;\n");
                writer.write("        }\n");
                for (FieldSpec field : fields) {
                    writer.write(loadStmt(field));
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
                writer.write("        if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID, VERSION)) {\n");
                writer.write("            return;\n");
                writer.write("        }\n");
                for (FieldSpec field : fields) {
                    writer.write("        if (tag.contains(" + field.schemaConstantName() + ".key())) {\n");
                    writer.write(applyDeltaStmt(field));
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
        String id = annotation.id();
        int delimiter = id.indexOf(':');
        if (delimiter <= 0 || delimiter != id.lastIndexOf(':') || delimiter == id.length() - 1) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiSyncModel.id must be a namespace:path resource location",
                    typeElement
            );
            return null;
        }
        String namespace = id.substring(0, delimiter);
        String path = id.substring(delimiter + 1);
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiSyncModel.id must be a namespace:path resource location",
                    typeElement
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

    private String loadStmt(FieldSpec field) {
        return readStmt(field, "        ");
    }

    private String writeDeltaStmt(FieldSpec field) {
        return "            tag.put(" + field.schemaConstantName() + ".key(), PiSchemaFieldCodecs.writeField(" +
                field.schemaConstantName() + ", self." + field.fieldName() + ").getSecond());\n";
    }

    private String applyDeltaStmt(FieldSpec field) {
        return readStmt(field, "            ");
    }

    private String readStmt(FieldSpec field, String indent) {
        String readCall = "PiSchemaFieldCodecs.readField(tag, " + field.schemaConstantName() + ", context, " + fallbackExpr(field) + ")";
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
                yield indent + field.valueType() + " " + decoded + " = " + readCall + ";\n"
                        + indent + "self." + field.fieldName() + ".clear();\n"
                        + indent + "self." + field.fieldName() + ".addAll(" + decoded + ");\n";
            }
            case MUTATE_MAP -> {
                String decoded = localDecodedName(field);
                yield indent + field.valueType() + " " + decoded + " = " + readCall + ";\n"
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
            String syncScope,
            boolean persist,
            String serializerExpression,
            FieldAccessStrategy accessStrategy
    ) {
    }

    private record ResolvedSerializer(String valueType, String serializerExpression, RawKind rawKind) {
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

    private record LivingServiceSpec(
            String namespace,
            String path,
            String serviceSimpleName,
            String stateQualifiedName,
            String stateSimpleName
    ) {
    }
}
