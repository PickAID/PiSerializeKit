package org.pickaid.piserializekit.processor.support;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.pickaid.piserializekit.processor.model.PiRawKind;
import org.pickaid.piserializekit.processor.model.PiResolvedSerializer;

public final class PiProcessorSerializerSupport {
    private PiProcessorSerializerSupport() {
    }

    public static PiResolvedSerializer resolveBuiltInScalarSerializer(
            Types types,
            TypeMirror type,
            boolean nestedSyncModel,
            boolean enumType
    ) {
        if (type.getKind() == TypeKind.BYTE || PiProcessorTypeSupport.sameType(type, "java.lang.Byte")) {
            return scalar("java.lang.Byte", "requireSerializer(PiSerializers.BYTE)");
        }
        if (type.getKind() == TypeKind.SHORT || PiProcessorTypeSupport.sameType(type, "java.lang.Short")) {
            return scalar("java.lang.Short", "requireSerializer(PiSerializers.SHORT)");
        }
        if (type.getKind() == TypeKind.INT || PiProcessorTypeSupport.sameType(type, "java.lang.Integer")) {
            return scalar("java.lang.Integer", "requireSerializer(PiSerializers.INT)");
        }
        if (type.getKind() == TypeKind.LONG || PiProcessorTypeSupport.sameType(type, "java.lang.Long")) {
            return scalar("java.lang.Long", "requireSerializer(PiSerializers.LONG)");
        }
        if (type.getKind() == TypeKind.BOOLEAN || PiProcessorTypeSupport.sameType(type, "java.lang.Boolean")) {
            return scalar("java.lang.Boolean", "requireSerializer(PiSerializers.BOOLEAN)");
        }
        if (type.getKind() == TypeKind.FLOAT || PiProcessorTypeSupport.sameType(type, "java.lang.Float")) {
            return scalar("java.lang.Float", "requireSerializer(PiSerializers.FLOAT)");
        }
        if (type.getKind() == TypeKind.DOUBLE || PiProcessorTypeSupport.sameType(type, "java.lang.Double")) {
            return scalar("java.lang.Double", "requireSerializer(PiSerializers.DOUBLE)");
        }
        if (PiProcessorTypeSupport.sameType(type, "java.lang.String")) {
            return scalar("java.lang.String", "requireSerializer(PiSerializers.STRING)");
        }
        if (PiProcessorTypeSupport.sameType(type, "java.util.UUID")) {
            return scalar("java.util.UUID", "requireSerializer(PiSerializers.UUID)");
        }
        if (PiProcessorTypeSupport.sameType(type, "net.minecraft.resources.ResourceLocation")) {
            return scalar("net.minecraft.resources.ResourceLocation", "requireSerializer(PiSerializers.RESOURCE_LOCATION)");
        }
        if (PiProcessorTypeSupport.sameType(type, "net.minecraft.nbt.CompoundTag")) {
            return scalar("net.minecraft.nbt.CompoundTag", "requireSerializer(PiSerializers.COMPOUND_TAG)");
        }
        if (PiProcessorTypeSupport.sameType(type, "net.minecraft.core.BlockPos")) {
            return scalar("net.minecraft.core.BlockPos", "requireSerializer(PiSerializers.BLOCK_POS)");
        }
        if (PiProcessorTypeSupport.sameType(type, "net.minecraft.world.phys.Vec3")) {
            return scalar("net.minecraft.world.phys.Vec3", "requireSerializer(PiSerializers.VEC3)");
        }
        if (PiProcessorTypeSupport.sameType(type, "net.minecraft.world.item.ItemStack")) {
            return scalar("net.minecraft.world.item.ItemStack", "requireSerializer(PiSerializers.ITEM_STACK)");
        }
        if (nestedSyncModel) {
            return scalar(type.toString(), "PiSchemaSerializers.forState(" + type + ".class)");
        }
        if (enumType) {
            return scalar(type.toString(), "PiSerializers.enumType(" + type + ".class)");
        }
        return null;
    }

    public static PiRawKind rawKind(Types types, TypeMirror type) {
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.List")) {
            return PiRawKind.LIST;
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.Set")) {
            return PiRawKind.SET;
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.Map")) {
            return PiRawKind.MAP;
        }
        return PiRawKind.SCALAR;
    }

    public static String validateCustomSerializerType(
            TypeElement serializerTypeElement
    ) {
        if (serializerTypeElement.getKind().isInterface()
                || serializerTypeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return "@PiField.serializer must reference a concrete codec provider class";
        }
        if (serializerTypeElement.getModifiers().contains(Modifier.PRIVATE)
                || serializerTypeElement.getNestingKind().isNested() && !serializerTypeElement.getModifiers().contains(Modifier.STATIC)) {
            return "@PiField.serializer must be an accessible top-level or static nested class";
        }
        return null;
    }

    public static String validateCustomSerializerValueType(Types types, TypeMirror fieldType, TypeMirror providerValueType) {
        if (providerValueType == null || !PiProcessorTypeSupport.isConcreteType(providerValueType)) {
            return "@PiField.serializer must bind PiFieldCodecProvider<T> through a concrete generic type";
        }
        TypeMirror expectedType = PiProcessorTypeSupport.comparableType(types, fieldType);
        TypeMirror actualType = PiProcessorTypeSupport.comparableType(types, providerValueType);
        if (!types.isSameType(expectedType, actualType)) {
            return "@PiField.serializer value type " + actualType + " does not match field type " + expectedType;
        }
        return null;
    }

    public static PiResolvedSerializer customSerializer(Types types, TypeMirror fieldType, TypeMirror serializerType) {
        TypeMirror comparableFieldType = PiProcessorTypeSupport.comparableType(types, fieldType);
        return new PiResolvedSerializer(
                PiProcessorTypeSupport.boxedTypeName(types, comparableFieldType),
                "new " + serializerType + "().serializer()",
                rawKind(types, fieldType)
        );
    }

    public static PiResolvedSerializer resolveCompositeSerializer(
            Types types,
            TypeMirror type,
            Element fieldElement,
            RecursiveSerializerResolver resolver,
            TypeArgumentResolver typeArgumentResolver
    ) {
        if (type.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            PiResolvedSerializer element = resolver.resolve(arrayType.getComponentType(), fieldElement);
            if (element == null) {
                return null;
            }
            return scalar(type.toString(), "PiSerializers.arrayOf(" + type + ".class, " + element.serializerExpression() + ")");
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.Optional")) {
            TypeMirror elementType = typeArgumentResolver.resolve(type, fieldElement, 0, "Optional");
            if (elementType == null) {
                return null;
            }
            PiResolvedSerializer element = resolver.resolve(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new PiResolvedSerializer(
                    "java.util.Optional<" + element.valueType() + ">",
                    "PiSerializers.optionalOf(" + element.serializerExpression() + ")",
                    PiRawKind.SCALAR
            );
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.List")) {
            TypeMirror elementType = typeArgumentResolver.resolve(type, fieldElement, 0, "List");
            if (elementType == null) {
                return null;
            }
            PiResolvedSerializer element = resolver.resolve(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new PiResolvedSerializer(
                    "java.util.List<" + element.valueType() + ">",
                    "PiSerializers.listOf(" + element.serializerExpression() + ")",
                    PiRawKind.LIST
            );
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.Set")) {
            TypeMirror elementType = typeArgumentResolver.resolve(type, fieldElement, 0, "Set");
            if (elementType == null) {
                return null;
            }
            PiResolvedSerializer element = resolver.resolve(elementType, fieldElement);
            if (element == null) {
                return null;
            }
            return new PiResolvedSerializer(
                    "java.util.Set<" + element.valueType() + ">",
                    "PiSerializers.setOf(" + element.serializerExpression() + ")",
                    PiRawKind.SET
            );
        }
        if (PiProcessorTypeSupport.sameErasure(types, type, "java.util.Map")) {
            TypeMirror keyType = typeArgumentResolver.resolve(type, fieldElement, 0, "Map");
            TypeMirror valueType = typeArgumentResolver.resolve(type, fieldElement, 1, "Map");
            if (keyType == null || valueType == null) {
                return null;
            }
            PiResolvedSerializer key = resolver.resolve(keyType, fieldElement);
            PiResolvedSerializer value = resolver.resolve(valueType, fieldElement);
            if (key == null || value == null) {
                return null;
            }
            return new PiResolvedSerializer(
                    "java.util.Map<" + key.valueType() + ", " + value.valueType() + ">",
                    "PiSerializers.mapOf(" + key.serializerExpression() + ", " + value.serializerExpression() + ")",
                    PiRawKind.MAP
            );
        }
        return null;
    }

    private static PiResolvedSerializer scalar(String valueType, String serializerExpression) {
        return new PiResolvedSerializer(valueType, serializerExpression, PiRawKind.SCALAR);
    }

    @FunctionalInterface
    public interface RecursiveSerializerResolver {
        PiResolvedSerializer resolve(TypeMirror type, Element fieldElement);
    }

    @FunctionalInterface
    public interface TypeArgumentResolver {
        TypeMirror resolve(TypeMirror type, Element fieldElement, int index, String label);
    }
}
