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
import javax.lang.model.element.TypeElement;
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
import org.pickaid.piserializekit.processor.model.PiChunkFacetSpec;
import org.pickaid.piserializekit.processor.model.PiFieldSpec;
import org.pickaid.piserializekit.processor.model.PiLevelFacetSpec;
import org.pickaid.piserializekit.processor.model.PiLivingFacetSpec;
import org.pickaid.piserializekit.processor.model.PiMigrationPlan;
import org.pickaid.piserializekit.processor.model.PiPacketSpec;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;
import org.pickaid.piserializekit.processor.model.PiSchemaIdentity;
import org.pickaid.piserializekit.processor.support.PiProcessorAnnotationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorChunkFacetGenerationSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorExecutableSupport;
import org.pickaid.piserializekit.processor.support.PiProcessorFacetAuthoringSupport;
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

@SupportedAnnotationTypes({
        "org.pickaid.piserializekit.api.schema.PiSyncModel",
        "org.pickaid.piserializekit.api.schema.PiField",
        "org.pickaid.piserializekit.api.schema.PiAfterDecode",
        "org.pickaid.piserializekit.api.schema.PiSchemaUpgrade",
        "org.pickaid.piserializekit.api.packet.PiPacket",
        "org.pickaid.piserializekit.api.packet.PiPacketUpgrade",
        "org.pickaid.pibrary.api.facet.PiLivingFacet",
        "org.pickaid.pibrary.api.facet.PiLevelFacet",
        "org.pickaid.pibrary.api.facet.PiChunkFacet"
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
    private static final String LIVING_FACET_ANNOTATION = "org.pickaid.pibrary.api.facet.PiLivingFacet";
    private static final String LIVING_FACET_CONTEXT = "org.pickaid.pibrary.api.facet.PiLivingFacetContext";
    private static final String GENERATED_LIVING_FACET_DESCRIPTOR = "org.pickaid.pibrary.runtime.facet.PiGeneratedLivingFacetDescriptor";
    private static final String LIVING_FACET_PROVIDER = "org.pickaid.pibrary.runtime.facet.PiLivingFacetProvider";
    private static final String LIVING_FACET_REGISTRY = "org.pickaid.pibrary.runtime.facet.PiLivingFacetRegistry";
    private static final String STATEFUL_LIVING_FACET_BASE = "org.pickaid.pibrary.api.facet.PiStateLivingEntityFacet";
    private static final String LEVEL_FACET_ANNOTATION = "org.pickaid.pibrary.api.facet.PiLevelFacet";
    private static final String LEVEL_FACET_CONTEXT = "org.pickaid.pibrary.api.facet.PiLevelFacetContext";
    private static final String GENERATED_LEVEL_FACET_DESCRIPTOR = "org.pickaid.pibrary.runtime.facet.PiGeneratedLevelFacetDescriptor";
    private static final String LEVEL_FACET_PROVIDER = "org.pickaid.pibrary.runtime.facet.PiLevelFacetProvider";
    private static final String LEVEL_FACET_REGISTRY = "org.pickaid.pibrary.runtime.facet.PiLevelFacetRegistry";
    private static final String STATEFUL_LEVEL_FACET_BASE = "org.pickaid.pibrary.api.facet.PiStateLevelFacet";
    private static final String CHUNK_FACET_ANNOTATION = "org.pickaid.pibrary.api.facet.PiChunkFacet";
    private static final String CHUNK_FACET_CONTEXT = "org.pickaid.pibrary.api.facet.PiChunkFacetContext";
    private static final String GENERATED_CHUNK_FACET_DESCRIPTOR = "org.pickaid.pibrary.runtime.facet.PiGeneratedChunkFacetDescriptor";
    private static final String CHUNK_FACET_PROVIDER = "org.pickaid.pibrary.runtime.facet.PiChunkFacetProvider";
    private static final String CHUNK_FACET_REGISTRY = "org.pickaid.pibrary.runtime.facet.PiChunkFacetRegistry";
    private static final String STATEFUL_CHUNK_FACET_BASE = "org.pickaid.pibrary.api.facet.PiStateChunkFacet";
    private static final String FIELD_ANNOTATION = PiField.class.getName();
    private static final String INFERRED_FIELD_CODEC = PiInferredFieldCodec.class.getName();

    private final Set<String> providerTypes = new LinkedHashSet<>();
    private final Set<String> packetProviderTypes = new LinkedHashSet<>();
    private final Set<String> livingFacetProviderTypes = new LinkedHashSet<>();
    private final Set<String> levelFacetProviderTypes = new LinkedHashSet<>();
    private final Set<String> chunkFacetProviderTypes = new LinkedHashSet<>();
    private final Map<String, String> schemaIds = new LinkedHashMap<>();
    private final Map<String, String> packetIds = new LinkedHashMap<>();
    private final Map<String, String> livingFacetIds = new LinkedHashMap<>();
    private final Map<String, String> levelFacetIds = new LinkedHashMap<>();
    private final Map<String, String> chunkFacetIds = new LinkedHashMap<>();
    private PiProcessorAnnotationSupport annotationSupport;
    private PiProcessorFieldAuthoringSupport fieldSupport;
    private PiProcessorPacketAuthoringSupport packetSupport;
    private PiProcessorFacetAuthoringSupport facetSupport;

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
        this.facetSupport = new PiProcessorFacetAuthoringSupport(
                processingEnv,
                annotationSupport,
                new PiProcessorFacetAuthoringSupport.FacetContract(
                        "@PiLivingFacet",
                        LIVING_FACET_ANNOTATION,
                        LIVING_FACET_CONTEXT,
                        STATEFUL_LIVING_FACET_BASE,
                        "PiStateLivingEntityFacet",
                        "facets"
                ),
                new PiProcessorFacetAuthoringSupport.FacetContract(
                        "@PiLevelFacet",
                        LEVEL_FACET_ANNOTATION,
                        LEVEL_FACET_CONTEXT,
                        STATEFUL_LEVEL_FACET_BASE,
                        "PiStateLevelFacet",
                        "facets"
                ),
                new PiProcessorFacetAuthoringSupport.FacetContract(
                        "@PiChunkFacet",
                        CHUNK_FACET_ANNOTATION,
                        CHUNK_FACET_CONTEXT,
                        STATEFUL_CHUNK_FACET_BASE,
                        "PiStateChunkFacet",
                        "facets"
                )
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writePacketProviderServiceFile();
            writeProviderServiceFile();
            writeLivingProviderServiceFile();
            writeLevelProviderServiceFile();
            writeChunkFacetProviderServiceFile();
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
        TypeElement livingFacetAnnotation = processingEnv.getElementUtils().getTypeElement(LIVING_FACET_ANNOTATION);
        if (livingFacetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(livingFacetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiLivingFacet")) {
                        continue;
                    }
                    PiLivingFacetSpec spec = facetSupport.resolveLivingFacetSpec(typeElement);
                    if (spec != null && reserveGeneratedId(typeElement, livingFacetIds, "living facet", spec.namespace(), spec.path())) {
                        PiProcessorLivingGenerationSupport.generateLivingDescriptorType(
                                processingEnv,
                                typeElement,
                                spec,
                                LIVING_FACET_CONTEXT,
                                GENERATED_LIVING_FACET_DESCRIPTOR
                        );
                        PiProcessorLivingGenerationSupport.generateLivingProviderType(
                                processingEnv,
                                typeElement,
                                livingFacetProviderTypes,
                                LIVING_FACET_PROVIDER,
                                LIVING_FACET_REGISTRY
                        );
                    }
                }
            }
        }
        TypeElement levelFacetAnnotation = processingEnv.getElementUtils().getTypeElement(LEVEL_FACET_ANNOTATION);
        if (levelFacetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(levelFacetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiLevelFacet")) {
                        continue;
                    }
                    PiLevelFacetSpec spec = facetSupport.resolveLevelFacetSpec(typeElement);
                    if (spec != null && reserveGeneratedId(typeElement, levelFacetIds, "level facet", spec.namespace(), spec.path())) {
                        PiProcessorLevelGenerationSupport.generateLevelDescriptorType(
                                processingEnv,
                                typeElement,
                                spec,
                                LEVEL_FACET_CONTEXT,
                                GENERATED_LEVEL_FACET_DESCRIPTOR
                        );
                        PiProcessorLevelGenerationSupport.generateLevelProviderType(
                                processingEnv,
                                typeElement,
                                levelFacetProviderTypes,
                                LEVEL_FACET_PROVIDER,
                                LEVEL_FACET_REGISTRY
                        );
                    }
                }
            }
        }
        TypeElement chunkFacetAnnotation = processingEnv.getElementUtils().getTypeElement(CHUNK_FACET_ANNOTATION);
        if (chunkFacetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(chunkFacetAnnotation)) {
                if (element instanceof TypeElement typeElement) {
                    if (!validateConcreteClassType(typeElement, "@PiChunkFacet")) {
                        continue;
                    }
                    PiChunkFacetSpec spec = facetSupport.resolveChunkFacetSpec(typeElement);
                    if (spec != null && reserveGeneratedId(typeElement, chunkFacetIds, "chunk facet", spec.namespace(), spec.path())) {
                        PiProcessorChunkFacetGenerationSupport.generateChunkDescriptorType(
                                processingEnv,
                                typeElement,
                                spec,
                                CHUNK_FACET_CONTEXT,
                                GENERATED_CHUNK_FACET_DESCRIPTOR
                        );
                        PiProcessorChunkFacetGenerationSupport.generateChunkProviderType(
                                processingEnv,
                                typeElement,
                                chunkFacetProviderTypes,
                                CHUNK_FACET_PROVIDER,
                                CHUNK_FACET_REGISTRY
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

    private boolean reserveGeneratedId(
            TypeElement typeElement,
            Map<String, String> reservedIds,
            String generatedKind,
            String namespace,
            String path
    ) {
        String id = namespace + ":" + path;
        String owner = typeElement.getQualifiedName().toString();
        String existing = reservedIds.putIfAbsent(id, owner);
        if (existing != null && !existing.equals(owner)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Duplicate Pi " + generatedKind + " id " + id + " already declared by " + existing,
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
                livingFacetProviderTypes,
                LIVING_FACET_PROVIDER,
                "Failed to generate Pi living facet provider service file"
        );
    }

    private void writeLevelProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                levelFacetProviderTypes,
                LEVEL_FACET_PROVIDER,
                "Failed to generate Pi level facet provider service file"
        );
    }

    private void writeChunkFacetProviderServiceFile() {
        PiProcessorServiceFileSupport.writeServiceFile(
                processingEnv,
                chunkFacetProviderTypes,
                CHUNK_FACET_PROVIDER,
                "Failed to generate Pi chunk facet provider service file"
        );
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
