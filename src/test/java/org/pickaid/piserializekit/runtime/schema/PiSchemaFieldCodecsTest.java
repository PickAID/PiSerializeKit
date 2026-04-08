package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;
import org.pickaid.piserializekit.api.service.PiSerializers;

class PiSchemaFieldCodecsTest {
    @Test
    void serializerBackedFieldRoundTripsAndRecordsIssues() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = PiSchemaSupport.tagOf(PiSchemaFieldCodecs.writeField(field, "boss"));
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("boss", PiSchemaFieldCodecs.readField(tag, field, context, "fallback"));
        assertFalse(context.result().hasFatal());
        assertEquals(0, context.result().issues().size());
    }

    @Test
    void invalidFieldPayloadUsesFallbackAndReportsIssue() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = new CompoundTag();
        tag.put("title", new CompoundTag());
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("fallback", PiSchemaFieldCodecs.readField(tag, field, context, "fallback"));
        assertEquals(1, context.result().issues().size());
        assertEquals("title", context.result().issues().get(0).path());
        assertTrue(context.result().issues().get(0).message().startsWith("failed to decode field payload:"));
        assertFalse(context.result().issues().get(0).fatal());
    }

    @Test
    void missingFieldUsesFallbackAndReportsIssue() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("fallback", PiSchemaFieldCodecs.readField(new CompoundTag(), field, context, "fallback"));
        assertEquals(1, context.result().issues().size());
        assertEquals("title", context.result().issues().get(0).path());
        assertEquals("missing field payload", context.result().issues().get(0).message());
        assertFalse(context.result().issues().get(0).fatal());
    }

    @Test
    void legacyFlatPayloadStillDecodes() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = new CompoundTag();
        tag.putString("title", "boss");
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("boss", PiSchemaFieldCodecs.readField(tag, field, context, "fallback"));
        assertEquals(0, context.result().issues().size());
    }
}
