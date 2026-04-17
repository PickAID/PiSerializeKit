package org.pickaid.piserializekit.processor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.api.packet.PiPacketUpgrade;
import org.pickaid.piserializekit.api.schema.PiAfterDecode;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiInferredFieldCodec;
import org.pickaid.piserializekit.api.schema.PiSchemaUpgrade;
import org.pickaid.piserializekit.api.schema.PiSyncModel;
import org.pickaid.piserializekit.processor.migration.PiMigrationCollectionResult;
import org.pickaid.piserializekit.processor.migration.PiMigrationValidationFailure;
import org.pickaid.piserializekit.processor.model.PiAfterDecodeSpec;
import org.pickaid.piserializekit.processor.model.PiLevelServiceSpec;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiLivingServiceSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiPacketSpec;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;
import org.pickaid.piserializekit.processor.model.PiSchemaIdentity;
import org.pickaid.piserializekit.processor.support.PiProcessorAnnotationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorExecutableSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorFieldAuthoringSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorLevelGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorLivingGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorMigrationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorNames;
import org.pickaid.piserializekit.processor.support.PiProcessorPacketGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorPacketAuthoringSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSchemaGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorSchemaSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorServiceFileSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorTypeSupport;

@SupportedAnnotationTypes({
        "org.pickaid.piserializekit.api.schema.PiSyncModel",
        "org.pickaid.piserializekit.api.schema.PiField",
        "org.pickaid.piserializekit.api.schema.PiAfterDecode",
        "org.pickaid.piserializekit.api.schema.PiSchemaUpgrade",
        "org.pickaid.piserializekit.api.packet.PiPacket",
        "org.pickaid.piserializekit.api.packet.PiPacketUpgrade",
        "org.pickaid.pibrary.api.service.PiLivingService",
        "org.pickaid.pibrary.api.service.PiLevelService"
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
    private static final String STATEFUL_LIVING_SERVICE_BASE = "org.pickaid.pibrary.api.service.PiStateLivingEntityService";
    private static final String LEVEL_SERVICE_ANNOTATION = "org.pickaid.pibrary.api.service.PiLevelService";
    private static final String LEVEL_SERVICE_CONTEXT = "org.pickaid.pibrary.api.service.PiLevelServiceContext";
    private static final String GENERATED_LEVEL_SERVICE_DESCRIPTOR = "org.pickaid.pibrary.runtime.level.PiGeneratedLevelServiceDescriptor";
    private static final String LEVEL_SERVICE_PROVIDER = "org.pickaid.pibrary.runtime.level.PiLevelServiceProvider";
    private static final String LEVEL_SERVICE_REGISTRY = "org.pickaid.pibrary.runtime.level.PiLevelServiceRegistry";
    private static final String STATEFUL_LEVEL_SERVICE_BASE = "org.pickaid.pibrary.api.service.PiStateLevelService";
    private static final String FIELD_ANNOTATION = PiField.class.getName();
    private static final String INFERRED_FIELD_CODEC = PiInferredFieldCodec.class.getName();

    private final Set<String> providerTypes = new LinkedHashSet<>();
    private final Set<String> packetProviderTypes = new LinkedHashSet<>();
    private final Set<String> livingProviderTypes = new LinkedHashSet<>();
    private final Set<String> levelProviderTypes = new LinkedHashSet<>();
    private final Map<String, String> schemaIds = new LinkedHashMap<>();
    private final Map<String, String> packetIds = new LinkedHashMap<>();
    private PiProcessorAnnotationSupport annotationSupport;
    private PiProcessorFieldAuthoringSupport fieldSupport;
    private PiProcessorPacketAuthoringSupport packetSupport;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.annotationSupport = new PiProcessorAnnotationSupport(processingEnv);
        this.fieldSupport = new PiProcessorFieldAuthoringSupport(
                processingEnv,
                annotationSupport,
                FIELD_ANNOTATION,
                INFERRED_FIELD_CODEC,
                PACKET_ANNOTATION
        );
        this.packetSupport = new PiProcessorPacketAuthoringSupport(
                processingEnv,
                annotationSupport,
                fieldSupport,
                PACKET_ANNOTATION,
                PACKET_NAMESPACE_ANNOTATION,
                SERVER_PACKET,
                CLIENT_PACKET,
                BIDIRECTIONAL_PACKET
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writePacketProviderServiceFile();
            writeProviderServiceFile();
            writeLivingProviderServiceFile();
            writeLevelProviderServiceFile();
            return false;
        }
        annotationSupport.validateAnnotationHosts(roundEnv, PACKET_ANNOTATION);
        TypeElement packetAnnotation = processingEnv.getElementUtils().getTypeElement(PACKET_ANNOTATION);
        if (packetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(packetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiPacket")) {
                        continue;
                    }
                    PiPacketSpec packetSpec = packetSupport.resolvePacketSpec(typeElement);
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
                List<PiFieldSpec> fields = fieldSupport.collectFields(typeElement);
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
        TypeElement levelServiceAnnotation = processingEnv.getElementUtils().getTypeElement(LEVEL_SERVICE_ANNOTATION);
        if (levelServiceAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(levelServiceAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiLevelService")) {
                        continue;
                    }
                    PiLevelServiceSpec spec = levelServiceSpec(typeElement);
                    if (spec != null) {
                        PiProcessorLevelGenerationSupport.generateLevelDescriptorType(
                                processingEnv,
                                typeElement,
                                spec,
                                LEVEL_SERVICE_CONTEXT,
                                GENERATED_LEVEL_SERVICE_DESCRIPTOR
                        );
                        PiProcessorLevelGenerationSupport.generateLevelProviderType(
                                processingEnv,
                                typeElement,
                                levelProviderTypes,
                                LEVEL_SERVICE_PROVIDER,
                                LEVEL_SERVICE_REGISTRY
                        );
                    }
                }
            }
        }
        return false;
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

    private void writeLevelProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                levelProviderTypes,
                LEVEL_SERVICE_PROVIDER,
                "Failed to generate Pi level service provider service file"
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
        AnnotationMirror mirror = annotationSupport.findAnnotation(typeElement, LIVING_SERVICE_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationSupport.annotationValues(mirror);
        String namespace = annotationSupport.stringValue(values, "namespace");
        String path = annotationSupport.stringValue(values, "path");
        if (namespace == null || path == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService requires namespace and path values",
                    typeElement
            );
            return null;
        }
        TypeMirror stateTypeMirror = PiProcessorTypeSupport.resolveConcreteTypeArgumentInHierarchy(
                processingEnv.getTypeUtils(),
                typeElement.asType(),
                STATEFUL_LIVING_SERVICE_BASE,
                0
        );
        if (stateTypeMirror == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService types must extend PiStateLivingEntityService<S> with a concrete state type",
                    typeElement
            );
            return null;
        }
        if (PiProcessorTypeSupport.isParameterizedDeclaredType(stateTypeMirror)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLivingService types must resolve to a non-parameterized concrete state type",
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

    private PiLevelServiceSpec levelServiceSpec(TypeElement typeElement) {
        PiProcessorExecutableSupport.MatchingConstructorStatus constructorStatus =
                PiProcessorExecutableSupport.matchingConstructorStatus(processingEnv, typeElement, LEVEL_SERVICE_CONTEXT);
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.MISSING) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLevelService types must declare an accessible constructor accepting PiLevelServiceContext",
                    typeElement
            );
            return null;
        }
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.THROWS_CHECKED) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLevelService constructors accepting PiLevelServiceContext must not throw checked exceptions because generated descriptors instantiate services directly",
                    typeElement
            );
            return null;
        }
        AnnotationMirror mirror = annotationSupport.findAnnotation(typeElement, LEVEL_SERVICE_ANNOTATION);
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationSupport.annotationValues(mirror);
        String namespace = annotationSupport.stringValue(values, "namespace");
        String path = annotationSupport.stringValue(values, "path");
        if (namespace == null || path == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLevelService requires namespace and path values",
                    typeElement
            );
            return null;
        }
        PiResolvedResourceLocation location = PiProcessorSchemaSupport.resolveExplicitResourceLocation(namespace + ":" + path);
        if (location == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    PiProcessorSchemaSupport.invalidResourceLocationMessage(
                            "@PiLevelService requires namespace and path values to form a valid namespace:path resource location",
                            namespace + ":" + path
                    ),
                    typeElement
            );
            return null;
        }
        namespace = location.namespace();
        path = location.path();
        TypeMirror stateTypeMirror = PiProcessorTypeSupport.resolveConcreteTypeArgumentInHierarchy(
                processingEnv.getTypeUtils(),
                typeElement.asType(),
                STATEFUL_LEVEL_SERVICE_BASE,
                0
        );
        if (stateTypeMirror == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLevelService types must extend PiStateLevelService<S> with a concrete state type",
                    typeElement
            );
            return null;
        }
        if (PiProcessorTypeSupport.isParameterizedDeclaredType(stateTypeMirror)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PiLevelService types must resolve to a non-parameterized concrete state type",
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
        return new PiLevelServiceSpec(namespace, path, serviceSimpleName, stateQualifiedName, stateSimpleName);
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

}
