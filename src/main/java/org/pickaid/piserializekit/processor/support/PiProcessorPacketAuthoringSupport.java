package org.pickaid.piserializekit.processor.support;

import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.processor.migration.PiMigrationCollectionResult;
import org.pickaid.piserializekit.processor.migration.PiMigrationValidationFailure;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiPacketDirectionSpec;
import org.pickaid.piserializekit.processor.model.PiPacketIdentity;
import org.pickaid.piserializekit.processor.model.PiPacketSpec;
import org.pickaid.piserializekit.processor.model.PiSchemaIdentity;

public final class PiProcessorPacketAuthoringSupport {
    private final ProcessingEnvironment processingEnv;
    private final PiProcessorAnnotationSupport annotations;
    private final PiProcessorFieldAuthoringSupport fieldSupport;
    private final String packetAnnotation;
    private final String packetNamespaceAnnotation;
    private final String serverPacket;
    private final String clientPacket;
    private final String bidirectionalPacket;

    public PiProcessorPacketAuthoringSupport(
            ProcessingEnvironment processingEnv,
            PiProcessorAnnotationSupport annotations,
            PiProcessorFieldAuthoringSupport fieldSupport,
            String packetAnnotation,
            String packetNamespaceAnnotation,
            String serverPacket,
            String clientPacket,
            String bidirectionalPacket
    ) {
        this.processingEnv = processingEnv;
        this.annotations = annotations;
        this.fieldSupport = fieldSupport;
        this.packetAnnotation = packetAnnotation;
        this.packetNamespaceAnnotation = packetNamespaceAnnotation;
        this.serverPacket = serverPacket;
        this.clientPacket = clientPacket;
        this.bidirectionalPacket = bidirectionalPacket;
    }

    public PiPacketSpec resolvePacketSpec(TypeElement typeElement) {
        AnnotationMirror mirror = annotations.findAnnotation(typeElement, packetAnnotation);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotations.annotationValues(mirror);
        Map<String, AnnotationValue> declaredValues = annotations.declaredAnnotationValues(mirror);
        PiPacketIdentity packetIdentity = resolvePacketIdentity(
                typeElement,
                declaredValues.containsKey("id"),
                annotations.stringValue(values, "id"),
                declaredValues.containsKey("namespace"),
                annotations.stringValue(values, "namespace"),
                declaredValues.containsKey("path"),
                annotations.stringValue(values, "path")
        );
        if (packetIdentity == null) {
            return null;
        }
        PiPacketDirectionSpec direction = resolvePacketDirection(typeElement);
        if (direction == null) {
            return null;
        }
        Integer version = annotations.intValue(values, "version");
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
        List<PiFieldSpec> fields = fieldSupport.collectFields(typeElement);
        if (fields == null) {
            return null;
        }
        if (!PiProcessorPacketConstructorSupport.hasCompatiblePacketConstructor(processingEnv, typeElement, fields)) {
            return null;
        }
        return new PiPacketSpec(packetIdentity.namespace(), packetIdentity.path(), packetVersion, direction, fields, migrations);
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
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, declaredIdError, typeElement);
            return null;
        }
        String declaredNamespaceError = PiProcessorPacketSupport.validateDeclaredPacketNamespace(declaredNamespace, explicitNamespace);
        if (declaredNamespaceError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, declaredNamespaceError, typeElement);
            return null;
        }
        String declaredPathError = PiProcessorPacketSupport.validateDeclaredPacketPath(declaredPath, explicitPath);
        if (declaredPathError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, declaredPathError, typeElement);
            return null;
        }
        String identityCombinationError = PiProcessorPacketSupport.validatePacketIdentityCombination(
                declaredId,
                declaredNamespace,
                declaredPath
        );
        if (identityCombinationError != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, identityCombinationError, typeElement);
            return null;
        }
        if (declaredId) {
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
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, namespaceError, typeElement);
                return null;
            }
            return explicitNamespace;
        }
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        AnnotationMirror packageMirror = annotations.findAnnotation(packageElement, packetNamespaceAnnotation);
        if (packageMirror == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorPacketSupport.missingPacketNamespaceMessage(),
                    typeElement
            );
            return null;
        }
        String namespace = annotations.stringValue(annotations.annotationValues(packageMirror), "value");
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
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, pathError, typeElement);
            return null;
        }
        return path;
    }

    private PiPacketDirectionSpec resolvePacketDirection(TypeElement typeElement) {
        if (isAssignableTo(typeElement, serverPacket)) {
            return new PiPacketDirectionSpec("SERVERBOUND");
        }
        if (isAssignableTo(typeElement, clientPacket)) {
            return new PiPacketDirectionSpec("CLIENTBOUND");
        }
        if (isAssignableTo(typeElement, bidirectionalPacket)) {
            return new PiPacketDirectionSpec("BIDIRECTIONAL");
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

    private PiSchemaIdentity resolveExplicitResourceLocation(String id, Element element, String errorMessage) {
        var location = PiProcessorSchemaSupport.resolveExplicitResourceLocation(id);
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
}
