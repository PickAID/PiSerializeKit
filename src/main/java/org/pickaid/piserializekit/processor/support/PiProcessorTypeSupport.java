package org.pickaid.piserializekit.processor.support;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.pickaid.piserializekit.api.schema.PiFieldCodecProvider;
import org.pickaid.piserializekit.api.schema.PiSyncModel;

public final class PiProcessorTypeSupport {
    private PiProcessorTypeSupport() {
    }

    public static boolean sameType(TypeMirror type, String qualifiedName) {
        return qualifiedName.equals(type.toString());
    }

    public static boolean sameErasure(Types types, TypeMirror type, String qualifiedName) {
        return qualifiedName.equals(types.erasure(type).toString());
    }

    public static TypeMirror resolveProviderValueType(Types types, TypeMirror providerType) {
        if (!(providerType instanceof DeclaredType declaredType)) {
            return null;
        }
        if (sameErasure(types, providerType, PiFieldCodecProvider.class.getName())) {
            return declaredType.getTypeArguments().size() == 1 ? declaredType.getTypeArguments().get(0) : null;
        }
        for (TypeMirror superType : types.directSupertypes(providerType)) {
            TypeMirror resolved = resolveProviderValueType(types, superType);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    public static boolean isConcreteType(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> true;
            case ARRAY -> isConcreteType(((ArrayType) type).getComponentType());
            case DECLARED -> {
                DeclaredType declaredType = (DeclaredType) type;
                boolean concrete = true;
                for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                    if (!isConcreteType(typeArgument)) {
                        concrete = false;
                        break;
                    }
                }
                yield concrete;
            }
            default -> false;
        };
    }

    public static TypeMirror comparableType(Types types, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return types.boxedClass((PrimitiveType) type).asType();
        }
        return type;
    }

    public static boolean isEnumType(Types types, TypeMirror type) {
        Element element = types.asElement(type);
        return element != null && element.getKind() == ElementKind.ENUM;
    }

    public static boolean isNestedSyncModelType(Types types, TypeMirror type) {
        Element element = types.asElement(type);
        return element instanceof TypeElement typeElement && typeElement.getAnnotation(PiSyncModel.class) != null;
    }

    public static TypeMirror resolveConcreteTypeArgument(TypeMirror type, int index) {
        if (!(type instanceof DeclaredType declaredType) || declaredType.getTypeArguments().size() <= index) {
            return null;
        }
        TypeMirror typeArgument = declaredType.getTypeArguments().get(index);
        return isConcreteType(typeArgument) ? typeArgument : null;
    }

    public static String boxedTypeName(Types types, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            TypeElement boxed = types.boxedClass((PrimitiveType) type);
            return boxed.getQualifiedName().toString();
        }
        return type.toString();
    }
}
