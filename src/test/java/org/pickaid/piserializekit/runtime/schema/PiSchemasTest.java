package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiStateBinding;

class PiSchemasTest {
    @Test
    void resolvesBindingByStateTypeThroughServiceLoader() {
        PiStateBinding<TestSchemaProvider.TestState> binding = PiSchemas.require(TestSchemaProvider.TestState.class);

        assertEquals(TestSchemaProvider.SCHEMA_ID, binding.schemaId());
        assertEquals(TestSchemaProvider.SCHEMA_VERSION, binding.version());

        assertEquals(TestSchemaProvider.TestState.class, binding.stateType());
        assertEquals(1, binding.fields().size());
        assertEquals(TestSchemaProvider.VALUE, binding.fields().get(0).key());
    }
}
