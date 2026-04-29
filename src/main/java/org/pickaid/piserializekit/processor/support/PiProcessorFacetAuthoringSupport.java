package org.pickaid.piserializekit.processor.support;

import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.pickaid.piserializekit.processor.model.PiChunkFacetSpec;
import org.pickaid.piserializekit.processor.model.PiLevelFacetSpec;
import org.pickaid.piserializekit.processor.model.PiLivingFacetSpec;
import org.pickaid.piserializekit.processor.model.PiResolvedResourceLocation;

public final class PiProcessorFacetAuthoringSupport {
    private final ProcessingEnvironment processingEnv;
    private final PiProcessorAnnotationSupport annotationSupport;
    private final FacetContract living;
    private final FacetContract level;
    private final FacetContract chunk;

    public PiProcessorFacetAuthoringSupport(
            ProcessingEnvironment processingEnv,
            PiProcessorAnnotationSupport annotationSupport,
            FacetContract living,
            FacetContract level,
            FacetContract chunk
    ) {
        this.processingEnv = processingEnv;
        this.annotationSupport = annotationSupport;
        this.living = living;
        this.level = level;
        this.chunk = chunk;
    }

    public PiLivingFacetSpec resolveLivingFacetSpec(TypeElement typeElement) {
        ResolvedFacetSpec spec = resolve(typeElement, living, false);
        return spec == null
                ? null
                : new PiLivingFacetSpec(spec.namespace(), spec.path(), spec.simpleName(), spec.stateQualifiedName(), spec.stateSimpleName());
    }

    public PiLevelFacetSpec resolveLevelFacetSpec(TypeElement typeElement) {
        ResolvedFacetSpec spec = resolve(typeElement, level, true);
        return spec == null
                ? null
                : new PiLevelFacetSpec(spec.namespace(), spec.path(), spec.simpleName(), spec.stateQualifiedName(), spec.stateSimpleName());
    }

    public PiChunkFacetSpec resolveChunkFacetSpec(TypeElement typeElement) {
        ResolvedFacetSpec spec = resolve(typeElement, chunk, true);
        return spec == null
                ? null
                : new PiChunkFacetSpec(spec.namespace(), spec.path(), spec.simpleName(), spec.stateQualifiedName(), spec.stateSimpleName());
    }

    private ResolvedFacetSpec resolve(TypeElement typeElement, FacetContract contract, boolean validateLocation) {
        PiProcessorExecutableSupport.MatchingConstructorStatus constructorStatus =
                PiProcessorExecutableSupport.matchingConstructorStatus(processingEnv, typeElement, contract.contextType());
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.MISSING) {
            error(contract.annotationName() + " types must declare an accessible constructor accepting " + contract.contextSimpleName(), typeElement);
            return null;
        }
        if (constructorStatus == PiProcessorExecutableSupport.MatchingConstructorStatus.THROWS_CHECKED) {
            error(contract.annotationName() + " constructors accepting " + contract.contextSimpleName()
                    + " must not throw checked exceptions because generated descriptors instantiate "
                    + contract.generatedNoun() + " directly", typeElement);
            return null;
        }
        AnnotationMirror mirror = annotationSupport.findAnnotation(typeElement, contract.annotationType());
        if (mirror == null) {
            return null;
        }
        Map<String, AnnotationValue> values = annotationSupport.annotationValues(mirror);
        String namespace = annotationSupport.stringValue(values, "namespace");
        String path = annotationSupport.stringValue(values, "path");
        if (namespace == null || path == null) {
            error(contract.annotationName() + " requires namespace and path values", typeElement);
            return null;
        }
        if (validateLocation) {
            PiResolvedResourceLocation location = PiProcessorSchemaSupport.resolveExplicitResourceLocation(namespace + ":" + path);
            if (location == null) {
                error(PiProcessorSchemaSupport.invalidResourceLocationMessage(
                        contract.annotationName() + " requires namespace and path values to form a valid namespace:path resource location",
                        namespace + ":" + path
                ), typeElement);
                return null;
            }
            namespace = location.namespace();
            path = location.path();
        }
        TypeMirror stateTypeMirror = PiProcessorTypeSupport.resolveConcreteTypeArgumentInHierarchy(
                processingEnv.getTypeUtils(),
                typeElement.asType(),
                contract.statefulBaseType(),
                0
        );
        if (stateTypeMirror == null) {
            error(contract.annotationName() + " types must extend " + contract.statefulBaseSimpleName()
                    + "<S> with a concrete state type", typeElement);
            return null;
        }
        if (PiProcessorTypeSupport.isParameterizedDeclaredType(stateTypeMirror)) {
            error(contract.annotationName() + " types must resolve to a non-parameterized concrete state type", typeElement);
            return null;
        }
        Element stateElement = processingEnv.getTypeUtils().asElement(stateTypeMirror);
        String stateQualifiedName = stateTypeMirror.toString();
        String stateSimpleName = stateElement instanceof TypeElement stateTypeElement
                ? stateTypeElement.getSimpleName().toString()
                : stateQualifiedName;
        return new ResolvedFacetSpec(
                namespace,
                path,
                typeElement.getSimpleName().toString(),
                stateQualifiedName,
                stateSimpleName
        );
    }

    private void error(String message, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    public record FacetContract(
            String annotationName,
            String annotationType,
            String contextType,
            String statefulBaseType,
            String statefulBaseSimpleName,
            String generatedNoun
    ) {
        public String contextSimpleName() {
            int lastDot = contextType.lastIndexOf('.');
            return lastDot < 0 ? contextType : contextType.substring(lastDot + 1);
        }
    }

    private record ResolvedFacetSpec(
            String namespace,
            String path,
            String simpleName,
            String stateQualifiedName,
            String stateSimpleName
    ) {
    }
}
