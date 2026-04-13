package org.pickaid.piserializekit.processor.support;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.pickaid.piserializekit.processor.migration.PiMigrationStepSpec;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiPacketSpec;

public final class PiProcessorPacketGenerationSupport {
    private PiProcessorPacketGenerationSupport() {
    }

    public static void generatePacketType(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            PiPacketSpec packetSpec
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                typeElement,
                typeElement.getSimpleName() + "_PiPacket"
        );
        String typeName = typeElement.getSimpleName().toString();
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), typeElement);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
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
                writer.write("public final class " + target.simpleName() + " {\n");
                writer.write("    public static final int VERSION = " + packetSpec.version() + ";\n");
                writer.write("    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(\"" + packetSpec.namespace() + "\", \"" + packetSpec.path() + "\");\n");
                if (!packetSpec.fields().isEmpty()) {
                    writer.write("\n");
                    for (PiFieldSpec field : packetSpec.fields()) {
                        writer.write("    public static final PiFieldKey " + field.constantName() + " = new PiFieldKey(" + field.index() + ", \"" + field.id() + "\");\n");
                    }
                    writer.write("    public static final List<PiFieldKey> FIELDS = List.of(" + joinConstantNames(packetSpec.fields()) + ");\n");
                    for (PiFieldSpec field : packetSpec.fields()) {
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
                for (PiFieldSpec field : packetSpec.fields()) {
                    writer.write("            " + packetSerializerConstantName(field) + ".packetCodec().write(buffer, value." + field.fieldName() + ");\n");
                }
                writer.write("        }\n\n");
                writer.write("        @Override\n");
                writer.write("        public " + typeName + " read(FriendlyByteBuf buffer, PiDecodeContext context) {\n");
                writer.write("            int incomingVersion = PiPacketSupport.safeRead(context, PiSchemaSupport.SCHEMA_VERSION_KEY, buffer::readVarInt, VERSION);\n");
                writer.write("            boolean legacy = incomingVersion < VERSION;\n");
                for (PiFieldSpec field : packetSpec.fields()) {
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
                for (PiFieldSpec field : packetSpec.fields()) {
                    writer.write("            PiPacketSupport.writePayloadField(payload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", " + packetIncomingValueName(field) + ", context);\n");
                }
                writer.write("            CompoundTag upgradedPayload = PiPacketSupport.upgradePacketPayload(PACKET_ID.toString(), incomingVersion, VERSION, payload, MIGRATIONS, context);\n");
                writer.write("            CompoundTag resolvedPayload = upgradedPayload == null ? new CompoundTag() : upgradedPayload;\n");
                for (PiFieldSpec field : packetSpec.fields()) {
                    writer.write("            " + field.valueType() + " " + packetDecodedValueName(field)
                            + " = PiSchemaFieldCodecs.decode(resolvedPayload, " + field.constantName() + ".id(), " + packetSerializerConstantName(field)
                            + ", context, " + packetFallbackExpr(field) + ");\n");
                }
                writer.write("            try {\n");
                writer.write("                return new " + typeName + "(" + joinDecodedValueNames(packetSpec.fields()) + ");\n");
                writer.write("            } catch (RuntimeException exception) {\n");
                writer.write("                    context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, \"\", PiSchemaSupport.describeException(exception, \"packet construction failed\"), true);\n");
                writer.write("                    return null;\n");
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
                writer.write("    private " + target.simpleName() + "() {\n");
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate " + target.qualifiedName(), exception);
        }
    }

    public static void generatePacketProviderType(
            ProcessingEnvironment processingEnv,
            TypeElement typeElement,
            Set<String> packetProviderTypes,
            String packetProvider,
            String packetRegistry
    ) {
        PiProcessorSourceSupport.GeneratedSourceTarget target = PiProcessorSourceSupport.generatedType(
                processingEnv,
                typeElement,
                typeElement.getSimpleName() + "_PiPacketProvider"
        );
        String packetSimpleName = typeElement.getSimpleName() + "_PiPacket";
        packetProviderTypes.add(target.qualifiedName());
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(target.qualifiedName(), typeElement);
            try (Writer writer = file.openWriter()) {
                writer.write(target.packageDeclaration());
                writer.write("import " + packetProvider + ";\n");
                writer.write("import " + packetRegistry + ";\n\n");
                writer.write("public final class " + target.simpleName() + " implements PiPacketProvider {\n");
                writer.write("    @Override\n");
                writer.write("    public void register(PiPacketRegistry registry) {\n");
                writer.write("        registry.register(" + typeElement.getSimpleName() + ".class, " + packetSimpleName + ".BINDING);\n");
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

    private static String packetSerializerConstantName(PiFieldSpec field) {
        return field.constantName() + "_SERIALIZER";
    }

    private static String packetDecodedValueName(PiFieldSpec field) {
        return "__pi_" + field.fieldName();
    }

    private static String packetIncomingValueName(PiFieldSpec field) {
        return "__pi_raw_" + field.fieldName();
    }

    private static String packetFallbackExpr(PiFieldSpec field) {
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

    private static String joinDecodedValueNames(List<PiFieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(packetDecodedValueName(fields.get(index)));
        }
        return builder.toString();
    }

    private static String joinIncomingValueNames(List<PiFieldSpec> fields) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(packetIncomingValueName(fields.get(index)));
        }
        return builder.toString();
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
}
