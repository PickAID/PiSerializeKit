package org.pickaid.piserializekit.runtime;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.packet.PiBidirectionalPacket;
import org.pickaid.piserializekit.api.packet.PiClientPacket;
import org.pickaid.piserializekit.api.packet.PiPacketBinding;
import org.pickaid.piserializekit.api.packet.PiPacketDirection;
import org.pickaid.piserializekit.api.packet.PiServerPacket;
import org.pickaid.piserializekit.api.runtime.PiRuntimeBindingValidationException;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.schema.PiStateBinding;

/**
 * Shared runtime validation for manual and generated Pi bindings.
 */
public final class PiRuntimeBindingValidation {
    private PiRuntimeBindingValidation() {
    }

    public static <T> void validateSchemaBinding(PiStateBinding<T> binding) {
        Objects.requireNonNull(binding, "binding");
        ResourceLocation schemaId = requireSchemaId(binding);
        if (binding.version() < 1) {
            throw invalidSchemaBinding(schemaId, "Pi schema binding version must be >= 1 for " + schemaId);
        }
        Class<?> stateType = requireSchemaStateType(binding, schemaId);
        if (!isConcreteClass(stateType)) {
            throw invalidSchemaBinding(schemaId, "Pi schema binding stateType must be a concrete class for " + schemaId);
        }
        validateNewStateFactory(schemaId, stateType, binding);
        validateSchemaFields(schemaId, requireSchemaFields(binding, schemaId));
        validateMigrations("Pi schema", schemaId.toString(), binding.version(), requireSchemaMigrations(binding, schemaId));
    }

    public static void validatePacketBinding(PiPacketBinding<?> binding) {
        Objects.requireNonNull(binding, "binding");
        ResourceLocation packetId = requirePacketId(binding);
        if (binding.version() < 1) {
            throw invalidPacketBinding(packetId, "Pi packet binding version must be >= 1 for " + packetId);
        }
        PiPacketDirection direction = requirePacketDirection(binding, packetId);
        Class<?> packetType = requirePacketType(binding, packetId);
        if (!isConcreteClass(packetType)) {
            throw invalidPacketBinding(packetId, "Pi packet binding packetType must be a concrete class for " + packetId);
        }
        validatePacketDirection(packetId, packetType, direction);
        requirePacketCodec(binding, packetId);
        validatePacketFields(packetId, requirePacketFields(binding, packetId));
        validateMigrations("Pi packet", packetId.toString(), binding.version(), requirePacketMigrations(binding, packetId));
    }

    private static void validateSchemaFields(ResourceLocation schemaId, List<PiFieldDescriptor> fields) {
        Set<String> fieldIds = new LinkedHashSet<>();
        boolean[] seenIndexes = new boolean[fields.size()];
        for (PiFieldDescriptor descriptor : fields) {
            if (descriptor == null) {
                throw invalidSchemaBinding(schemaId, "Pi schema binding fields() must not contain null entries for " + schemaId);
            }
            validateFieldKey("Pi schema", schemaId.toString(), descriptor.key(), fieldIds, seenIndexes);
        }
        ensureContiguousIndexes("Pi schema", schemaId.toString(), seenIndexes);
    }

    private static void validatePacketFields(ResourceLocation packetId, List<PiFieldKey> fields) {
        Set<String> fieldIds = new LinkedHashSet<>();
        boolean[] seenIndexes = new boolean[fields.size()];
        for (PiFieldKey key : fields) {
            if (key == null) {
                throw invalidPacketBinding(packetId, "Pi packet binding fields() must not contain null entries for " + packetId);
            }
            validateFieldKey("Pi packet", packetId.toString(), key, fieldIds, seenIndexes);
        }
        ensureContiguousIndexes("Pi packet", packetId.toString(), seenIndexes);
    }

    private static void validateFieldKey(
            String bindingKind,
            String bindingId,
            PiFieldKey key,
            Set<String> fieldIds,
            boolean[] seenIndexes
    ) {
        if (!isValidPayloadKey(key.id())) {
            throw invalidBinding(bindingKind, bindingId, bindingKind + " field id must be a valid payload key in binding " + bindingId + ": " + key.id());
        }
        if (isReservedPayloadKey(key.id())) {
            throw invalidBinding(bindingKind, bindingId, bindingKind + " field id uses reserved Pi payload prefix in binding " + bindingId + ": " + key.id());
        }
        if (!fieldIds.add(key.id())) {
            throw invalidBinding(bindingKind, bindingId, "Duplicate " + bindingKind.toLowerCase() + " field id " + key.id() + " in binding " + bindingId);
        }
        if (key.index() >= seenIndexes.length || seenIndexes[key.index()]) {
            throw invalidBinding(bindingKind, bindingId, bindingKind + " field indexes must cover [0.." + Math.max(0, seenIndexes.length - 1) + "] in binding " + bindingId);
        }
        seenIndexes[key.index()] = true;
    }

    private static void ensureContiguousIndexes(String bindingKind, String bindingId, boolean[] seenIndexes) {
        for (boolean seenIndex : seenIndexes) {
            if (!seenIndex) {
                throw invalidBinding(bindingKind, bindingId, bindingKind + " field indexes must cover [0.." + Math.max(0, seenIndexes.length - 1) + "] in binding " + bindingId);
            }
        }
    }

    private static void validateMigrations(String bindingKind, String bindingId, int targetVersion, List<PiSchemaMigration> migrations) {
        Map<Integer, PiSchemaMigration> indexed = new LinkedHashMap<>();
        for (PiSchemaMigration migration : migrations) {
            if (migration == null) {
                throw invalidBinding(bindingKind, bindingId, bindingKind + " binding migrations() must not contain null entries for " + bindingId);
            }
            PiSchemaMigration previous = indexed.putIfAbsent(migration.fromVersion(), migration);
            if (previous != null) {
                throw invalidBinding(bindingKind, bindingId, "Duplicate " + bindingKind.toLowerCase() + " migration from version "
                        + migration.fromVersion() + " in binding " + bindingId);
            }
            if (migration.fromVersion() >= targetVersion) {
                throw invalidBinding(bindingKind, bindingId, bindingKind + " migration source version must be below target version "
                        + targetVersion + " in binding " + bindingId + ": " + migration.fromVersion());
            }
            if (migration.toVersion() > targetVersion) {
                throw invalidBinding(bindingKind, bindingId, bindingKind + " migration target version must be <= binding version "
                        + targetVersion + " in binding " + bindingId + ": " + migration.toVersion());
            }
        }
        for (int startVersion = 1; startVersion < targetVersion; startVersion++) {
            int currentVersion = startVersion;
            while (currentVersion < targetVersion) {
                PiSchemaMigration migration = indexed.get(currentVersion);
                if (migration == null) {
                    throw invalidBinding(
                            bindingKind,
                            bindingId,
                            bindingKind + " migration chain must define a path from version "
                                    + startVersion + " to " + targetVersion
                                    + " in binding " + bindingId
                                    + "; missing step from version " + currentVersion
                                    + ". Declared steps: " + describeDeclaredSteps(migrations)
                    );
                }
                currentVersion = migration.toVersion();
            }
        }
    }

    private static ResourceLocation requireSchemaId(PiStateBinding<?> binding) {
        ResourceLocation schemaId = binding.schemaId();
        if (schemaId == null) {
            throw new PiRuntimeBindingValidationException(
                    "schema-binding-validation",
                    binding.getClass().getName(),
                    "Pi schema binding schemaId() must return a non-null id for " + binding.getClass().getName()
            );
        }
        return schemaId;
    }

    private static Class<?> requireSchemaStateType(PiStateBinding<?> binding, ResourceLocation schemaId) {
        Class<?> stateType = binding.stateType();
        if (stateType == null) {
            throw invalidSchemaBinding(
                    schemaId,
                    "Pi schema binding stateType() must return a non-null class for " + schemaId
            );
        }
        return stateType;
    }

    private static List<PiFieldDescriptor> requireSchemaFields(PiStateBinding<?> binding, ResourceLocation schemaId) {
        List<PiFieldDescriptor> fields = binding.fields();
        if (fields == null) {
            throw invalidSchemaBinding(
                    schemaId,
                    "Pi schema binding fields() must return a non-null list for " + schemaId
            );
        }
        return fields;
    }

    private static List<PiSchemaMigration> requireSchemaMigrations(PiStateBinding<?> binding, ResourceLocation schemaId) {
        List<PiSchemaMigration> migrations = binding.migrations();
        if (migrations == null) {
            throw invalidSchemaBinding(
                    schemaId,
                    "Pi schema binding migrations() must return a non-null list for " + schemaId
            );
        }
        return migrations;
    }

    private static ResourceLocation requirePacketId(PiPacketBinding<?> binding) {
        ResourceLocation packetId = binding.packetId();
        if (packetId == null) {
            throw new PiRuntimeBindingValidationException(
                    "packet-binding-validation",
                    binding.getClass().getName(),
                    "Pi packet binding packetId() must return a non-null id for " + binding.getClass().getName()
            );
        }
        return packetId;
    }

    private static PiPacketDirection requirePacketDirection(PiPacketBinding<?> binding, ResourceLocation packetId) {
        PiPacketDirection direction = binding.direction();
        if (direction == null) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding direction() must return a non-null direction for " + packetId
            );
        }
        return direction;
    }

    private static Class<?> requirePacketType(PiPacketBinding<?> binding, ResourceLocation packetId) {
        Class<?> packetType = binding.packetType();
        if (packetType == null) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding packetType() must return a non-null class for " + packetId
            );
        }
        return packetType;
    }

    private static void requirePacketCodec(PiPacketBinding<?> binding, ResourceLocation packetId) {
        if (binding.codec() == null) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding codec() must return a non-null codec for " + packetId
            );
        }
    }

    private static List<PiFieldKey> requirePacketFields(PiPacketBinding<?> binding, ResourceLocation packetId) {
        List<PiFieldKey> fields = binding.fields();
        if (fields == null) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding fields() must return a non-null list for " + packetId
            );
        }
        return fields;
    }

    private static List<PiSchemaMigration> requirePacketMigrations(PiPacketBinding<?> binding, ResourceLocation packetId) {
        List<PiSchemaMigration> migrations = binding.migrations();
        if (migrations == null) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding migrations() must return a non-null list for " + packetId
            );
        }
        return migrations;
    }

    private static String describeDeclaredSteps(List<PiSchemaMigration> migrations) {
        if (migrations.isEmpty()) {
            return "<none>";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < migrations.size(); index++) {
            PiSchemaMigration migration = migrations.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(migration.fromVersion()).append("->").append(migration.toVersion());
        }
        return builder.toString();
    }

    private static void validatePacketDirection(ResourceLocation packetId, Class<?> packetType, PiPacketDirection direction) {
        PiPacketDirection implied = impliedPacketDirection(packetType);
        if (implied != null && implied != direction) {
            throw invalidPacketBinding(
                    packetId,
                    "Pi packet binding direction " + direction
                            + " conflicts with packet base type " + packetType.getName()
                            + " which implies " + implied
            );
        }
    }

    private static <T> T validateNewStateFactory(ResourceLocation schemaId, Class<?> stateType, PiStateBinding<T> binding) {
        T first = createState(schemaId, stateType, binding);
        T second = createState(schemaId, stateType, binding);
        if (first == second) {
            throw invalidSchemaBinding(schemaId, "Pi schema binding newState() must return a fresh instance for " + schemaId);
        }
        return first;
    }

    private static <T> T createState(ResourceLocation schemaId, Class<?> stateType, PiStateBinding<T> binding) {
        T state;
        try {
            state = binding.newState();
        } catch (RuntimeException exception) {
            throw invalidSchemaBinding(schemaId, "Pi schema binding newState() threw for " + schemaId, exception);
        }
        if (state == null) {
            throw invalidSchemaBinding(schemaId, "Pi schema binding newState() must return a non-null instance for " + schemaId);
        }
        if (!stateType.isInstance(state)) {
            throw invalidSchemaBinding(
                    schemaId,
                    "Pi schema binding newState() returned " + state.getClass().getName()
                            + " which is not assignable to " + stateType.getName()
                            + " for " + schemaId
            );
        }
        return state;
    }

    private static PiRuntimeBindingValidationException invalidSchemaBinding(ResourceLocation schemaId, String message) {
        return new PiRuntimeBindingValidationException("schema-binding-validation", schemaId.toString(), message);
    }

    private static PiRuntimeBindingValidationException invalidSchemaBinding(ResourceLocation schemaId, String message, Throwable cause) {
        return new PiRuntimeBindingValidationException("schema-binding-validation", schemaId.toString(), message, cause);
    }

    private static PiRuntimeBindingValidationException invalidPacketBinding(ResourceLocation packetId, String message) {
        return new PiRuntimeBindingValidationException("packet-binding-validation", packetId.toString(), message);
    }

    private static PiRuntimeBindingValidationException invalidBinding(String bindingKind, String bindingId, String message) {
        return new PiRuntimeBindingValidationException(validationCategory(bindingKind), bindingId, message);
    }

    private static String validationCategory(String bindingKind) {
        return switch (bindingKind) {
            case "Pi schema" -> "schema-binding-validation";
            case "Pi packet" -> "packet-binding-validation";
            default -> "binding-validation";
        };
    }

    private static boolean isValidPayloadKey(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isLowercaseResourceChar(current) && current != '.' && current != '-' && current != '/') {
                return false;
            }
        }
        return true;
    }

    private static boolean isReservedPayloadKey(String value) {
        return value.startsWith("__pi_");
    }

    private static boolean isConcreteClass(Class<?> type) {
        return !type.isInterface()
                && !type.isPrimitive()
                && !type.isArray()
                && !type.isAnnotation()
                && !Modifier.isAbstract(type.getModifiers());
    }

    private static PiPacketDirection impliedPacketDirection(Class<?> packetType) {
        if (PiServerPacket.class.isAssignableFrom(packetType)) {
            return PiPacketDirection.SERVERBOUND;
        }
        if (PiClientPacket.class.isAssignableFrom(packetType)) {
            return PiPacketDirection.CLIENTBOUND;
        }
        if (PiBidirectionalPacket.class.isAssignableFrom(packetType)) {
            return PiPacketDirection.BIDIRECTIONAL;
        }
        return null;
    }

    private static boolean isLowercaseResourceChar(char current) {
        return current == '_' || current >= 'a' && current <= 'z' || current >= '0' && current <= '9';
    }
}
