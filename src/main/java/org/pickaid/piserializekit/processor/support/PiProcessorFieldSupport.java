package org.pickaid.piserializekit.processor.support;

import java.util.Set;
import javax.lang.model.element.Modifier;
import org.pickaid.piserializekit.processor.model.PiFieldAccessStrategy;
import org.pickaid.piserializekit.processor.model.PiRawKind;

public final class PiProcessorFieldSupport {
    private PiProcessorFieldSupport() {
    }

    public static String validateFieldAuthoringMode(
            boolean syncModelField,
            boolean packetField,
            boolean declaredId,
            String declaredIdValue,
            boolean declaredSync,
            boolean declaredPersist,
            String fieldName,
            String syncScope,
            boolean persist,
            String deltaMode
    ) {
        if (syncModelField) {
            if (declaredId && (declaredIdValue == null || declaredIdValue.isBlank())) {
                return "@PiField.id on @PiSyncModel field " + fieldName + " must be non-blank";
            }
            if (declaredId && declaredSync && declaredPersist) {
                return null;
            }
            return "@PiField on @PiSyncModel types must explicitly declare id, sync, and persist";
        }
        if (!packetField) {
            return null;
        }
        if (persist) {
            return "@PiField.persist on @PiPacket field " + fieldName + " must stay false because packet fields are transport-only";
        }
        if (!"OWNER".equals(syncScope)) {
            return "@PiField.sync on @PiPacket field " + fieldName
                    + " must stay OWNER because packet fields do not participate in state visibility routing";
        }
        if (!"REPLACE".equals(deltaMode)) {
            return "@PiField.delta on @PiPacket field " + fieldName
                    + " must stay REPLACE because packet payloads do not apply field deltas";
        }
        return null;
    }

    public static String validateFieldModifiers(String fieldName, Set<Modifier> modifiers) {
        if (modifiers.contains(Modifier.STATIC)) {
            return "@PiField field " + fieldName
                    + " must not be static because generated bindings only support instance state";
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            return "@PiField field " + fieldName
                    + " must not be private because generated bindings access fields directly";
        }
        if (modifiers.contains(Modifier.TRANSIENT)) {
            return "@PiField field " + fieldName
                    + " must not be transient because PiSerializeKit already controls persistence and transport semantics";
        }
        return null;
    }

    public static String validateDeltaMode(String deltaMode, String rawKind, boolean nestedSyncModel) {
        return switch (deltaMode) {
            case "REPLACE" -> null;
            case "NESTED_UPDATE" -> nestedSyncModel && "SCALAR".equals(rawKind)
                    ? null
                    : "@PiField(delta = NESTED_UPDATE) requires a nested @PiSyncModel field";
            case "MERGE_SET" -> "SET".equals(rawKind)
                    ? null
                    : "@PiField(delta = MERGE_SET) requires a Set field";
            case "MERGE_MAP" -> "MAP".equals(rawKind)
                    ? null
                    : "@PiField(delta = MERGE_MAP) requires a Map field";
            default -> "Unsupported Pi field delta mode " + deltaMode;
        };
    }

    public static String resolveFieldId(String fieldName, String annotationId) {
        return annotationId.isBlank() ? PiProcessorNames.camelToSnake(fieldName) : annotationId;
    }

    public static String validatePayloadKey(String fieldName, String fieldId) {
        if (!PiProcessorNames.isValidPayloadKey(fieldId)) {
            return "@PiField.id for field " + fieldName + " must resolve to a valid payload key";
        }
        if (PiProcessorNames.isReservedPayloadKey(fieldId)) {
            return "@PiField.id for field " + fieldName + " uses reserved Pi payload prefix __pi_";
        }
        return null;
    }

    public static PiFieldAccessStrategy resolveAccessStrategy(boolean finalField, PiRawKind rawKind) {
        if (!finalField) {
            return PiFieldAccessStrategy.ASSIGN;
        }
        return switch (rawKind) {
            case LIST -> PiFieldAccessStrategy.MUTATE_LIST;
            case SET -> PiFieldAccessStrategy.MUTATE_SET;
            case MAP -> PiFieldAccessStrategy.MUTATE_MAP;
            case SCALAR -> null;
        };
    }
}
