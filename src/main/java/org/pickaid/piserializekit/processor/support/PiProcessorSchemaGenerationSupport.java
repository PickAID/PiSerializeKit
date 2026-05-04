package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.migration.PiMigrationStepSpec;
import org.pickaid.piserializekit.processor.model.PiAfterDecodeSpec;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiSchemaIdentity;

public final class PiProcessorSchemaGenerationSupport {
    private PiProcessorSchemaGenerationSupport() {
    }

    public static void generateFieldsType(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            List<PiFieldSpec> fields
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                typeElement,
                typeElement.getSimpleName() + "_PiFields"
        );
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), typeElement);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import org.pickaid.piserializekit.api.schema.PiFieldKey;\n\n");
                writer.write("public final class " + target.simpleName() + " {\n");
                for (PiFieldSpec field : fields) {
                    writer.write("    public static final PiFieldKey " + field.constantName() + " = new PiFieldKey(" + field.index() + ", \"" + field.id() + "\");\n");
                }
                if (!fields.isEmpty()) {
                    writer.write("\n");
                }
                writer.write("    private " + target.simpleName() + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateSchemaType(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            List<PiFieldSpec> fields,
            PiSchemaIdentity schemaIdentity,
            PiAfterDecodeSpec afterDecode,
            PiMigrationPlan migrations
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                typeElement,
                typeElement.getSimpleName() + "_PiSchema"
        );
        String schemaId = schemaIdentity.id();
        int version = typeElement.getAnnotation(org.pickaid.piserializekit.api.schema.PiSyncModel.class).version();
        int fieldCount = fields.size();
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), typeElement);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
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
                writer.write("public final class " + target.simpleName() + " {\n");
                writer.write("    public static final String SCHEMA_ID = \"" + schemaId + "\";\n");
                writer.write("    public static final int VERSION = " + version + ";\n");
                writer.write("    public static final int FIELD_COUNT = " + fieldCount + ";\n");
                if (!fields.isEmpty()) {
                    writer.write("\n");
                    for (PiFieldSpec field : fields) {
                        writer.write("    public static final PiFieldDescriptor " + field.constantName() + " = " + descriptorExpr(typeElement, field) + ";\n");
                    }
                    writer.write("    public static final List<PiFieldDescriptor> FIELDS = List.of(" + joinConstantNames(fields) + ");\n");
                    for (PiFieldSpec field : fields) {
                        writer.write("    public static final PiSchemaField<" + field.valueType() + "> " + field.schemaConstantName() + " = new PiSchemaField<>("
                                + field.constantName() + ", " + field.serializerExpression() + ");\n");
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
                writeTagWithHeader(writer, syncedFields(fields));
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag savePersisted(" + typeElement.getSimpleName() + " self) {\n");
                writeTagWithHeader(writer, persistedFields(fields));
                writer.write("    }\n\n");
                writer.write("    public static PiStateSnapshot snapshot(" + typeElement.getSimpleName() + " self) {\n");
                writer.write("        return new PiStateSnapshot(BINDING.schemaId(), VERSION, new net.minecraft.nbt.Tag[]{\n");
                for (int index = 0; index < fields.size(); index++) {
                    PiFieldSpec field = fields.get(index);
                    writer.write("                PiSchemaFieldCodecs.encodeField(" + field.schemaConstantName() + ", self." + field.fieldName() + ")");
                    writer.write(index + 1 < fields.size() ? ",\n" : "\n");
                }
                writer.write("        });\n");
                writer.write("    }\n\n");
                writer.write("    public static PiDirtyBits diff(" + typeElement.getSimpleName() + " self, PiStateSnapshot snapshot) {\n");
                writer.write("        if (!snapshot.matches(BINDING)) {\n");
                writer.write("            throw new IllegalArgumentException(\"PiStateSnapshot does not match binding \" + BINDING.schemaId());\n");
                writer.write("        }\n");
                writer.write("        PiDirtyBits bits = new PiDirtyBits();\n");
                for (PiFieldSpec field : fields) {
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
                for (PiFieldSpec field : fields) {
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
                for (PiFieldSpec field : persistedFields(fields)) {
                    writer.write(loadStmt(field, "__pi_payload", "PiSchemaPayloadKind.PERSISTED"));
                }
                if (afterDecode.present()) {
                    writer.write("        self." + afterDecode.methodName() + "();\n");
                }
                writer.write("    }\n\n");
                writer.write("    public static CompoundTag writeDelta(" + typeElement.getSimpleName() + " self, PiDirtySet dirtySet) {\n");
                writer.write("        CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);\n");
                for (PiFieldSpec field : fields) {
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
                for (PiFieldSpec field : fields) {
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
                writer.write("    private " + target.simpleName() + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generateSchemaProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            Set<String> providerTypes
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                typeElement,
                typeElement.getSimpleName() + "_PiSchemaProvider"
        );
        providerTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), typeElement);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaProvider;\n");
                writer.write("import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiSchemaProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiSchemaRegistry registry) {\n");
                writer.write("        registry.register(" + typeElement.getSimpleName() + ".class, " + typeElement.getSimpleName() + "_PiSchema.BINDING);\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    private static void writeMigrationConstant(Writer writer, TypeElement typeElement, PiMigrationPlan migrations) throws IOException {
        String typeName = typeElement.getSimpleName().toString();
        if (!migrations.present()) {
            writer.write("    public static final List<PiSchemaMigration> MIGRATIONS = List.of();\n\n");
            return;
        }
        writer.write("    public static final List<PiSchemaMigration> MIGRATIONS = List.of(\n");
        for (int index = 0; index < migrations.steps().size(); index++) {
            PiMigrationStepSpec step = migrations.steps().get(index);
            writer.write("            PiSchemaMigration.step(" + step.fromVersion() + ", " + step.toVersion() + ", " + typeName + "::" + step.methodName() + ")");
            writer.write(index + 1 < migrations.steps().size() ? ",\n" : "\n");
        }
        writer.write("    );\n\n");
    }

    private static void writeBindingConstant(Writer writer, TypeElement typeElement, PiSchemaIdentity schemaIdentity) throws IOException {
        String typeName = typeElement.getSimpleName().toString();
        writer.write("    public static final PiStateBinding<" + typeName + "> BINDING = new PiStateBinding<>() {\n");
        writer.write("        @Override\n");
        writer.write("        public ResourceLocation schemaId() {\n");
        writer.write("            return new ResourceLocation(\"" + schemaIdentity.namespace() + "\", \"" + schemaIdentity.path() + "\");\n");
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
        writer.write("        public CompoundTag writeDelta(" + typeName + " self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {\n");
        writer.write("            return " + typeName + "_PiSchema.writeDelta(self, dirtySet);\n");
        writer.write("        }\n\n");
        writer.write("        @Override\n");
        writer.write("        public void applyDelta(" + typeName + " self, CompoundTag tag, PiDecodeContext context) {\n");
        writer.write("            " + typeName + "_PiSchema.applyDelta(self, tag, context);\n");
        writer.write("        }\n");
        writer.write("    };\n\n");
    }

    private static String joinConstantNames(List<PiFieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(fields.get(index).constantName());
        }
        return builder.toString();
    }

    private static String joinSchemaConstantNames(List<PiFieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(fields.get(index).schemaConstantName());
        }
        return builder.toString();
    }

    private static List<PiFieldSpec> syncedFields(List<PiFieldSpec> fields) {
        List<PiFieldSpec> synced = new ArrayList<>();
        for (PiFieldSpec field : fields) {
            if (isClientVisibleScope(field.syncScope())) {
                synced.add(field);
            }
        }
        return synced;
    }

    private static List<PiFieldSpec> persistedFields(List<PiFieldSpec> fields) {
        List<PiFieldSpec> persisted = new ArrayList<>();
        for (PiFieldSpec field : fields) {
            if (field.persist()) {
                persisted.add(field);
            }
        }
        return persisted;
    }

    private static boolean isClientVisibleScope(String syncScope) {
        return switch (syncScope) {
            case "CHUNK", "TRACKING", "GLOBAL" -> true;
            case "NONE", "OWNER", "MENU" -> false;
            default -> throw new IllegalStateException("Unsupported Pi sync scope for client view generation: " + syncScope);
        };
    }

    private static void writeTagWithHeader(Writer writer, List<PiFieldSpec> fields) throws IOException {
        writer.write("        CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID, VERSION);\n");
        for (PiFieldSpec field : fields) {
            writer.write("        PiSchemaFieldCodecs.writeField(tag, " + field.schemaConstantName() + ", self." + field.fieldName() + ");\n");
        }
        writer.write("        return tag;\n");
    }

    private static String loadStmt(PiFieldSpec field, String payloadName, String payloadKindExpression) {
        return readStmt(field, "        ", payloadName, payloadKindExpression, false);
    }

    private static String writeDeltaStmt(PiFieldSpec field) {
        return "            PiSchemaFieldCodecs.writeField(tag, " + field.schemaConstantName() + ", self." + field.fieldName() + ");\n";
    }

    private static String applyDeltaStmt(PiFieldSpec field, String payloadName, String payloadKindExpression) {
        return readStmt(field, "            ", payloadName, payloadKindExpression, true);
    }

    private static String readStmt(
            PiFieldSpec field,
            String indent,
            String payloadName,
            String payloadKindExpression,
            boolean deltaApply
    ) {
        if (field.nestedSyncModel() && field.deltaMode().equals("NESTED_UPDATE")) {
            return indent + "self." + field.fieldName() + " = PiSchemaFieldCodecs.readNestedField(" + payloadName + ", "
                    + field.schemaConstantName() + ".key(), PiSchemas.require(" + field.valueType() + ".class), context, self."
                    + field.fieldName() + ", " + payloadKindExpression + ");\n";
        }
        String readCall = "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName() + ", context, " + fallbackExpr(field) + ")";
        return switch (field.accessStrategy()) {
            case ASSIGN -> indent + "self." + field.fieldName() + " = " + readCall + ";\n";
            case MUTATE_LIST -> indent + "PiSchemaFieldCodecs.readFieldInto(" + payloadName + ", " + field.schemaConstantName()
                    + ", context, self." + field.fieldName() + ");\n";
            case MUTATE_SET -> {
                if (deltaApply && field.deltaMode().equals("MERGE_SET")) {
                    String decoded = localDecodedName(field);
                    String prefix = indent + field.valueType() + " " + decoded + " = PiSchemaFieldCodecs.readFieldOrNull(" + payloadName + ", "
                            + field.schemaConstantName() + ", context);\n";
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
                    String prefix = indent + field.valueType() + " " + decoded + " = PiSchemaFieldCodecs.readFieldOrNull(" + payloadName + ", "
                            + field.schemaConstantName() + ", context);\n";
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

    private static String localDecodedName(PiFieldSpec field) {
        return "__pi_" + field.fieldName() + "Decoded";
    }

    private static String fallbackExpr(PiFieldSpec field) {
        return switch (field.accessStrategy()) {
            case ASSIGN -> "self." + field.fieldName();
            case MUTATE_LIST -> "new java.util.ArrayList<>(self." + field.fieldName() + ")";
            case MUTATE_SET -> "new java.util.LinkedHashSet<>(self." + field.fieldName() + ")";
            case MUTATE_MAP -> "new java.util.LinkedHashMap<>(self." + field.fieldName() + ")";
        };
    }

    private static String descriptorExpr(TypeElement typeElement, PiFieldSpec field) {
        String base = typeElement.getSimpleName() + "_PiFields." + field.constantName() + ", PiSyncScope." + field.syncScope() + ", " + field.persist();
        if ("REPLACE".equals(field.deltaMode())) {
            return "new PiFieldDescriptor(" + base + ")";
        }
        return "new PiFieldDescriptor(" + base + ", PiFieldDeltaMode." + field.deltaMode() + ")";
    }
}
