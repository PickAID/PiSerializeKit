package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.runtime.PiRuntimeBindingValidationException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeConflictException;
import org.pickaid.piserializekit.api.runtime.PiRuntimeLookupException;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;
import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.runtime.schema.registry.PiSchemas;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

class PiSchemasTest {
    @Test
    void convenienceSaveAndLoadUseStateTypeBinding() throws Exception {
        GeneratedProjectionState source = new GeneratedProjectionState();
        source.phase = 3;
        source.rewardLabel = "boss";
        source.menuPage = 5;
        Method saveFull = PiSchemas.class.getMethod("saveFull", Object.class);
        Method loadFull = PiSchemas.class.getMethod("loadFull", Class.class, CompoundTag.class);

        CompoundTag tag = (CompoundTag) saveFull.invoke(null, source);
        Object decoded = loadFull.invoke(null, GeneratedProjectionState.class, tag);

        assertNotNull(decoded);
        assertTrue(decoded instanceof GeneratedProjectionState);
        assertEquals(3, ((GeneratedProjectionState) decoded).phase);
        assertEquals("boss", ((GeneratedProjectionState) decoded).rewardLabel);
        assertEquals(5, ((GeneratedProjectionState) decoded).menuPage);
    }

    @Test
    void resolvesBindingByStateTypeThroughServiceLoader() {
        PiStateBinding<TestSchemaProvider.TestState> binding = PiSchemas.require(TestSchemaProvider.TestState.class);

        assertEquals(TestSchemaProvider.SCHEMA_ID, binding.schemaId());
        assertEquals(TestSchemaProvider.SCHEMA_VERSION, binding.version());

        assertEquals(TestSchemaProvider.TestState.class, binding.stateType());
        assertEquals(1, binding.fields().size());
        assertEquals(TestSchemaProvider.VALUE, binding.fields().get(0).key());
        assertTrue(PiSchemas.schemaIds().contains(TestSchemaProvider.SCHEMA_ID));
        assertTrue(PiSchemas.stateTypes().contains(TestSchemaProvider.TestState.class));
    }

    @Test
    void resolvesBindingBySchemaIdThroughServiceLoader() {
        PiStateBinding<?> binding = PiSchemas.require(TestSchemaProvider.SCHEMA_ID);

        assertEquals(TestSchemaProvider.SCHEMA_ID, binding.schemaId());
        assertEquals(TestSchemaProvider.TestState.class, binding.stateType());
    }

    @Test
    void findsBindingsByTypeAndSchemaIdThroughServiceLoader() {
        assertTrue(PiSchemas.find(TestSchemaProvider.TestState.class).isPresent());
        assertTrue(PiSchemas.find(TestSchemaProvider.SCHEMA_ID).isPresent());
        assertTrue(PiSchemas.find(new ResourceLocation("test", "missing")).isEmpty());
    }

    @Test
    void schemaRegistryExposesKnownSchemaIdsAndTypes() throws Exception {
        PiSchemaRegistry registry = newRegistry();
        registry.register(TestSchemaProvider.TestState.class, PiSchemas.require(TestSchemaProvider.TestState.class));

        assertEquals(List.of(TestSchemaProvider.SCHEMA_ID), registry.schemaIds());
        assertEquals(List.of(TestSchemaProvider.TestState.class), registry.stateTypes());
    }

    @Test
    void rejectsDuplicateBindingsForSameStateType() throws Exception {
        PiSchemaRegistry registry = newRegistry();
        PiStateBinding<TestSchemaProvider.TestState> binding = PiSchemas.require(TestSchemaProvider.TestState.class);
        registry.register(TestSchemaProvider.TestState.class, binding);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> registry.register(TestSchemaProvider.TestState.class, new DuplicateTypeBinding())
        );

        assertEquals(
                "Duplicate Pi schema binding for type " + TestSchemaProvider.TestState.class.getName()
                        + "; existing schema id " + TestSchemaProvider.SCHEMA_ID
                        + ", conflicting schema id test:duplicate_type",
                exception.getMessage()
        );
    }

    @Test
    void rejectsDuplicateBindingsForSameSchemaId() throws Exception {
        PiSchemaRegistry registry = newRegistry();
        PiStateBinding<TestSchemaProvider.TestState> binding = PiSchemas.require(TestSchemaProvider.TestState.class);
        registry.register(TestSchemaProvider.TestState.class, binding);

        PiRuntimeConflictException exception = assertThrows(
                PiRuntimeConflictException.class,
                () -> registry.register(OtherState.class, new DuplicateSchemaIdBinding())
        );

        assertEquals(
                "Duplicate Pi schema binding for id " + TestSchemaProvider.SCHEMA_ID
                        + "; existing state type " + TestSchemaProvider.TestState.class.getName()
                        + ", conflicting state type " + OtherState.class.getName(),
                exception.getMessage()
        );
        assertEquals("schema-id", exception.category());
        assertEquals(TestSchemaProvider.SCHEMA_ID.toString(), exception.key());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rejectsSchemaBindingWhenRegisteredAgainstDifferentType() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        PiRuntimeBindingValidationException exception = assertThrows(
                PiRuntimeBindingValidationException.class,
                () -> registry.register((Class) String.class, (PiStateBinding) new DuplicateSchemaIdBinding())
        );

        assertEquals(
                "Binding state type mismatch: " + String.class.getName() + " != " + OtherState.class.getName(),
                exception.getMessage()
        );
        assertEquals("schema-binding-validation", exception.category());
        assertEquals(TestSchemaProvider.SCHEMA_ID.toString(), exception.bindingId());
    }

    @Test
    void missingSchemaBindingReportsKnownSchemaIds() throws Exception {
        PiSchemaRegistry registry = newRegistry();
        registry.register(TestSchemaProvider.TestState.class, PiSchemas.require(TestSchemaProvider.TestState.class));

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(String.class)
        );

        assertEquals(
                "Missing Pi schema binding for java.lang.String; known schema ids: " + TestSchemaProvider.SCHEMA_ID,
                exception.getMessage()
        );
    }

    @Test
    void missingSchemaBindingByIdReportsKnownSchemaIds() throws Exception {
        PiSchemaRegistry registry = newRegistry();
        registry.register(TestSchemaProvider.TestState.class, PiSchemas.require(TestSchemaProvider.TestState.class));

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(new ResourceLocation("test", "missing"))
        );

        assertEquals(
                "Missing Pi schema binding for test:missing; known schema ids: " + TestSchemaProvider.SCHEMA_ID,
                exception.getMessage()
        );
        assertEquals("schema-id", exception.category());
        assertEquals("test:missing", exception.key());
    }

    @Test
    void missingSchemaBindingOnEmptyRegistryIncludesProviderHint() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        PiRuntimeLookupException exception = assertThrows(
                PiRuntimeLookupException.class,
                () -> registry.require(OtherState.class)
        );

        assertEquals(
                "Missing Pi schema binding for " + OtherState.class.getName()
                        + "; known schema ids: <none>; no PiSchemaProvider entries were loaded. Ensure annotation processing generated schema providers and META-INF/services resources are on the runtime classpath.",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSchemaBindingWithNonPositiveVersion() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        PiRuntimeBindingValidationException exception = assertThrows(
                PiRuntimeBindingValidationException.class,
                () -> registry.register(OtherState.class, new InvalidSchemaVersionBinding())
        );

        assertEquals("Pi schema binding version must be >= 1 for test:invalid_schema_version", exception.getMessage());
        assertEquals("schema-binding-validation", exception.category());
        assertEquals("test:invalid_schema_version", exception.bindingId());
    }

    @Test
    void rejectsSchemaBindingWithInvalidFieldIds() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new InvalidSchemaFieldsBinding())
        );

        assertEquals("Pi schema field id must be a valid payload key in binding test:invalid_schema_fields: Count Value", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithReservedFieldPrefix() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new ReservedSchemaFieldsBinding())
        );

        assertEquals("Pi schema field id uses reserved Pi payload prefix in binding test:reserved_schema_fields: __pi_value", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithSparseFieldIndexes() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new SparseSchemaFieldsBinding())
        );

        assertEquals("Pi schema field indexes must cover [0..1] in binding test:sparse_schema_fields", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithMigrationTargetAboveBindingVersion() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new InvalidSchemaMigrationsBinding())
        );

        assertEquals(
                "Pi schema migration target version must be <= binding version 2 in binding test:invalid_schema_migrations: 3",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSchemaBindingWithIncompleteMigrationChain() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new IncompleteSchemaMigrationsBinding())
        );

        assertEquals(
                "Pi schema migration chain must define a path from version 1 to 4 in binding test:incomplete_schema_migrations; "
                        + "missing step from version 2. Declared steps: 1->2, 3->4",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSchemaBindingWithNullStateTypeBeforeTypeMismatchPath() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullStateTypeBinding())
        );

        assertEquals("Pi schema binding stateType() must return a non-null class for test:null_state_type", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithNullFieldsList() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullSchemaFieldsBinding())
        );

        assertEquals("Pi schema binding fields() must return a non-null list for test:null_schema_fields", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithNullSchemaId() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullSchemaIdBinding())
        );

        assertEquals(
                "Pi schema binding schemaId() must return a non-null id for "
                        + NullSchemaIdBinding.class.getName(),
                exception.getMessage()
        );
    }

    @Test
    void rejectsSchemaBindingWithNullMigrationsList() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullSchemaMigrationsBinding())
        );

        assertEquals("Pi schema binding migrations() must return a non-null list for test:null_schema_migrations", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithNullFieldEntry() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullSchemaFieldEntryBinding())
        );

        assertEquals("Pi schema binding fields() must not contain null entries for test:null_schema_field_entry", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithNullMigrationEntry() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullSchemaMigrationEntryBinding())
        );

        assertEquals("Pi schema binding migrations() must not contain null entries for test:null_schema_migration_entry", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWithAbstractStateType() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(AbstractState.class, new AbstractStateBinding())
        );

        assertEquals("Pi schema binding stateType must be a concrete class for test:abstract_state_binding", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWhenNewStateReturnsNull() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new NullNewStateBinding())
        );

        assertEquals("Pi schema binding newState() must return a non-null instance for test:null_new_state_binding", exception.getMessage());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rejectsSchemaBindingWhenNewStateReturnsWrongRuntimeType() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new WrongNewStateTypeBinding())
        );

        assertEquals(
                "Pi schema binding newState() returned " + DifferentState.class.getName()
                        + " which is not assignable to " + OtherState.class.getName()
                        + " for test:wrong_new_state_type_binding",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSchemaBindingWhenNewStateThrows() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new ThrowingNewStateBinding())
        );

        assertEquals("Pi schema binding newState() threw for test:throwing_new_state_binding", exception.getMessage());
    }

    @Test
    void rejectsSchemaBindingWhenNewStateReturnsSharedInstance() throws Exception {
        PiSchemaRegistry registry = newRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(OtherState.class, new SharedNewStateBinding())
        );

        assertEquals(
                "Pi schema binding newState() must return a fresh instance for test:shared_new_state_binding",
                exception.getMessage()
        );
    }


    private static PiSchemaRegistry newRegistry() throws Exception {
        Class<?> registryType = Class.forName("org.pickaid.piserializekit.runtime.schema.registry.PiSchemas$ServiceLoaderRegistry");
        Constructor<?> constructor = registryType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object registry = constructor.newInstance();
        java.lang.reflect.Field loaded = registryType.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(registry, true);
        return (PiSchemaRegistry) registry;
    }

    private static final class DuplicateTypeBinding implements PiStateBinding<TestSchemaProvider.TestState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "duplicate_type");
        }

        @Override
        public int version() {
            return TestSchemaProvider.SCHEMA_VERSION;
        }

        @Override
        public Class<TestSchemaProvider.TestState> stateType() {
            return TestSchemaProvider.TestState.class;
        }

        @Override
        public TestSchemaProvider.TestState newState() {
            return new TestSchemaProvider.TestState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return PiSchemas.require(TestSchemaProvider.TestState.class).fields();
        }

        @Override
        public CompoundTag saveFull(TestSchemaProvider.TestState self) {
            return PiSchemas.require(TestSchemaProvider.TestState.class).saveFull(self);
        }

        @Override
        public void loadFull(TestSchemaProvider.TestState self, CompoundTag tag, PiDecodeContext context) {
            PiSchemas.require(TestSchemaProvider.TestState.class).loadFull(self, tag, context);
        }

        @Override
        public CompoundTag saveClientView(TestSchemaProvider.TestState self) {
            return PiSchemas.require(TestSchemaProvider.TestState.class).saveClientView(self);
        }

        @Override
        public CompoundTag writeDelta(TestSchemaProvider.TestState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemas.require(TestSchemaProvider.TestState.class).writeDelta(self, dirtySet);
        }

        @Override
        public void applyDelta(TestSchemaProvider.TestState self, CompoundTag tag, PiDecodeContext context) {
            PiSchemas.require(TestSchemaProvider.TestState.class).applyDelta(self, tag, context);
        }
    }

    private static final class OtherState {
    }

    private abstract static class AbstractState {
    }

    private static final class DifferentState {
    }

    private static final class DuplicateSchemaIdBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return TestSchemaProvider.SCHEMA_ID;
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullNewStateBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_new_state_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return null;
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class WrongNewStateTypeBinding implements PiStateBinding {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "wrong_new_state_type_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class stateType() {
            return OtherState.class;
        }

        @Override
        public Object newState() {
            return new DifferentState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(Object self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(Object self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(Object self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(Object self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(Object self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class ThrowingNewStateBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "throwing_new_state_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            throw new IllegalStateException("boom");
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class SharedNewStateBinding implements PiStateBinding<OtherState> {
        private static final OtherState SHARED = new OtherState();

        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "shared_new_state_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return SHARED;
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }


    private static final class InvalidSchemaVersionBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "invalid_schema_version");
        }

        @Override
        public int version() {
            return 0;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), 1);
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), 1);
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class InvalidSchemaFieldsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "invalid_schema_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(new PiFieldDescriptor(new PiFieldKey(0, "Count Value"), org.pickaid.piserializekit.api.schema.PiSyncScope.TRACKING, true));
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class InvalidSchemaMigrationsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "invalid_schema_migrations");
        }

        @Override
        public int version() {
            return 2;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(PiSchemaMigration.step(1, 3, (payload, kind, context) -> payload));
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class ReservedSchemaFieldsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "reserved_schema_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(new PiFieldDescriptor(new PiFieldKey(0, "__pi_value"), org.pickaid.piserializekit.api.schema.PiSyncScope.TRACKING, true));
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class SparseSchemaFieldsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "sparse_schema_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(
                    new PiFieldDescriptor(new PiFieldKey(0, "first"), org.pickaid.piserializekit.api.schema.PiSyncScope.TRACKING, true),
                    new PiFieldDescriptor(new PiFieldKey(2, "third"), org.pickaid.piserializekit.api.schema.PiSyncScope.TRACKING, true)
            );
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullStateTypeBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_state_type");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return null;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullSchemaIdBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return null;
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return new CompoundTag();
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return new CompoundTag();
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullSchemaFieldsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_schema_fields");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return null;
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullSchemaMigrationsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_schema_migrations");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return null;
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullSchemaFieldEntryBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_schema_field_entry");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return java.util.Arrays.asList((PiFieldDescriptor) null);
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class NullSchemaMigrationEntryBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "null_schema_migration_entry");
        }

        @Override
        public int version() {
            return 2;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return java.util.Arrays.asList((PiSchemaMigration) null);
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class AbstractStateBinding implements PiStateBinding<AbstractState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "abstract_state_binding");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public Class<AbstractState> stateType() {
            return AbstractState.class;
        }

        @Override
        public AbstractState newState() {
            return null;
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public CompoundTag saveFull(AbstractState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(AbstractState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(AbstractState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(AbstractState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(AbstractState self, CompoundTag tag, PiDecodeContext context) {
        }
    }

    private static final class IncompleteSchemaMigrationsBinding implements PiStateBinding<OtherState> {
        @Override
        public ResourceLocation schemaId() {
            return new ResourceLocation("test", "incomplete_schema_migrations");
        }

        @Override
        public int version() {
            return 4;
        }

        @Override
        public Class<OtherState> stateType() {
            return OtherState.class;
        }

        @Override
        public OtherState newState() {
            return new OtherState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of();
        }

        @Override
        public List<PiSchemaMigration> migrations() {
            return List.of(
                    PiSchemaMigration.step(1, 2, (payload, kind, context) -> payload),
                    PiSchemaMigration.step(3, 4, (payload, kind, context) -> payload)
            );
        }

        @Override
        public CompoundTag saveFull(OtherState self) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void loadFull(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }

        @Override
        public CompoundTag saveClientView(OtherState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(OtherState self, org.pickaid.piserializekit.api.schema.PiDirtySet dirtySet) {
            return PiSchemaSupport.headerTag(schemaId(), version());
        }

        @Override
        public void applyDelta(OtherState self, CompoundTag tag, PiDecodeContext context) {
        }
    }
}
