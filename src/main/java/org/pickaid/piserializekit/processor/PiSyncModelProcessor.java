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
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import org.pickaid.piserializekit.api.schema.PiField;
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
                List<FieldSpec> fields = collectFields(typeElement);
                generateFieldsType(typeElement, fields);
                generateSchemaType(typeElement, fields, schemaIdentity);
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
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD || enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            PiField piField = enclosedElement.getAnnotation(PiField.class);
            if (piField == null) {
                continue;
            }
            fields.add(new FieldSpec(
                    index++,
                    constantName(enclosedElement.getSimpleName().toString()),
                    enclosedElement.getSimpleName().toString(),
                    piField.id(),
                    enclosedElement.asType().toString(),
                    piField.sync().name(),
                    piField.persist()
            ));
        }
        return fields;
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

    private void generateSchemaType(TypeElement typeElement, List<FieldSpec> fields, SchemaIdentity schemaIdentity) {
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
                    writer.write("    public static final List<PiFieldDescriptor> FIELDS = List.of(" + joinConstantNames(fields) + ");\n\n");
                } else {
                    writer.write("    public static final List<PiFieldDescriptor> FIELDS = List.of();\n\n");
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
                    writer.write("        if (tag.contains(\"" + field.id() + "\")) {\n");
                    writer.write(applyDeltaStmt(field));
                    writer.write("        }\n");
                }
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
        if (delimiter <= 0 || delimiter == id.length() - 1) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiSyncModel.id must be a namespace:path resource location",
                    typeElement
            );
            return null;
        }
        return new SchemaIdentity(id, id.substring(0, delimiter), id.substring(delimiter + 1));
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

    private List<FieldSpec> syncedFields(List<FieldSpec> fields) {
        List<FieldSpec> synced = new ArrayList<>();
        for (FieldSpec field : fields) {
            if (!"NONE".equals(field.syncScope())) {
                synced.add(field);
            }
        }
        return synced;
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
        return switch (field.javaType()) {
            case "int" -> "PiSchemaSupport.putInt(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "long" -> "PiSchemaSupport.putLong(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "boolean" -> "PiSchemaSupport.putBoolean(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "java.lang.String" -> "PiSchemaSupport.putString(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "java.util.UUID" -> "PiSchemaSupport.putUUID(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "net.minecraft.resources.ResourceLocation" -> "PiSchemaSupport.putResourceLocation(\"" + field.id() + "\", self." + field.fieldName() + ")";
            case "java.util.List<java.lang.String>" -> "PiSchemaSupport.putStringList(\"" + field.id() + "\", self." + field.fieldName() + ")";
            default -> throw new IllegalStateException("Unsupported field type for generation: " + field.javaType());
        };
    }

    private String loadStmt(FieldSpec field) {
        return switch (field.javaType()) {
            case "int" -> "        self." + field.fieldName() + " = PiSchemaSupport.getInt(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "long" -> "        self." + field.fieldName() + " = PiSchemaSupport.getLong(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "boolean" -> "        self." + field.fieldName() + " = PiSchemaSupport.getBoolean(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.lang.String" -> "        self." + field.fieldName() + " = PiSchemaSupport.getString(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.util.UUID" -> "        self." + field.fieldName() + " = PiSchemaSupport.getUUID(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "net.minecraft.resources.ResourceLocation" -> "        self." + field.fieldName() + " = PiSchemaSupport.getResourceLocation(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.util.List<java.lang.String>" -> "        self." + field.fieldName() + ".clear();\n" +
                    "        self." + field.fieldName() + ".addAll(PiSchemaSupport.getStringList(tag, \"" + field.id() + "\", context));\n";
            default -> throw new IllegalStateException("Unsupported field type for generation: " + field.javaType());
        };
    }

    private String writeDeltaStmt(FieldSpec field) {
        return switch (field.javaType()) {
            case "int" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putInt(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "long" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putLong(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "boolean" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putBoolean(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "java.lang.String" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putString(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "java.util.UUID" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putUUID(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "net.minecraft.resources.ResourceLocation" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putResourceLocation(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            case "java.util.List<java.lang.String>" -> "            tag.put(\"" + field.id() + "\", PiSchemaSupport.putStringList(\"" + field.id() + "\", self." + field.fieldName() + ").getSecond());\n";
            default -> throw new IllegalStateException("Unsupported field type for generation: " + field.javaType());
        };
    }

    private String applyDeltaStmt(FieldSpec field) {
        return switch (field.javaType()) {
            case "int" -> "            self." + field.fieldName() + " = PiSchemaSupport.getInt(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "long" -> "            self." + field.fieldName() + " = PiSchemaSupport.getLong(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "boolean" -> "            self." + field.fieldName() + " = PiSchemaSupport.getBoolean(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.lang.String" -> "            self." + field.fieldName() + " = PiSchemaSupport.getString(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.util.UUID" -> "            self." + field.fieldName() + " = PiSchemaSupport.getUUID(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "net.minecraft.resources.ResourceLocation" -> "            self." + field.fieldName() + " = PiSchemaSupport.getResourceLocation(tag, \"" + field.id() + "\", context, self." + field.fieldName() + ");\n";
            case "java.util.List<java.lang.String>" -> "            self." + field.fieldName() + ".clear();\n" +
                    "            self." + field.fieldName() + ".addAll(PiSchemaSupport.getStringList(tag, \"" + field.id() + "\", context));\n";
            default -> throw new IllegalStateException("Unsupported field type for generation: " + field.javaType());
        };
    }

    private AnnotationMirror findAnnotation(TypeElement typeElement, String annotationName) {
        for (AnnotationMirror annotationMirror : typeElement.getAnnotationMirrors()) {
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

    private record FieldSpec(int index, String constantName, String fieldName, String id, String javaType, String syncScope, boolean persist) {
    }

    private record SchemaIdentity(String id, String namespace, String path) {
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
