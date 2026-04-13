package org.pickaid.piserializekit.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.nbt.PiNbtCodec;
import org.pickaid.piserializekit.api.packet.PiPacketCodec;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDecodeIssueCode;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaField;
import org.pickaid.piserializekit.runtime.schema.codec.PiSchemaFieldCodecs;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;
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
    void directFieldWriteEncodesIntoExistingTag() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = PiSchemaSupport.headerTag("test:bench", 1);
        PiSchemaFieldCodecs.writeField(tag, field, "boss");

        assertEquals("boss", PiSchemaFieldCodecs.readField(tag, field, PiDecodeContext.strict(), "fallback"));
    }

    @Test
    void writeFieldUsesRawTagForSingleValueWrapperPayloads() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = PiSchemaSupport.headerTag("test:bench", 1);
        PiSchemaFieldCodecs.writeField(tag, field, "boss");

        Tag stored = tag.get("title");
        assertTrue(stored instanceof StringTag, () -> "Expected raw StringTag but got " + stored);
        assertEquals("boss", PiSchemaFieldCodecs.readField(tag, field, PiDecodeContext.strict(), "fallback"));
    }

    @Test
    void codecBackedNbtCodecDecodesRawTagDirectly() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        assertEquals(
                "boss",
                runtime.lookup(PiSerializers.STRING).orElseThrow().nbtCodec().decodeTag(StringTag.valueOf("boss"))
        );
    }

    @Test
    void codecBackedNbtCodecEncodesRawTagDirectly() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        Tag encoded = runtime.lookup(PiSerializers.STRING).orElseThrow().nbtCodec().encodeTag("boss");

        assertTrue(encoded instanceof StringTag, () -> "Expected raw StringTag but got " + encoded);
        assertEquals("boss", ((StringTag) encoded).getAsString());
    }

    @Test
    void codecBackedNbtCodecStillDecodesLegacyWrapperCompound() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        CompoundTag wrapped = new CompoundTag();
        wrapped.putString("__pi_value", "boss");

        assertEquals(
                "boss",
                runtime.lookup(PiSerializers.STRING).orElseThrow().nbtCodec().decodeTag(wrapped)
        );
    }

    @Test
    void writeFieldPreservesStructuredCompoundPayloads() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<CompoundTag> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "data"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.COMPOUND_TAG).orElseThrow()
        );

        CompoundTag payload = new CompoundTag();
        payload.putString("inner", "boss");
        CompoundTag tag = PiSchemaSupport.headerTag("test:bench", 1);
        PiSchemaFieldCodecs.writeField(tag, field, payload);

        Tag stored = tag.get("data");
        assertTrue(stored instanceof CompoundTag, () -> "Expected structured CompoundTag but got " + stored);
        assertEquals("boss", ((CompoundTag) stored).getString("inner"));
    }

    @Test
    void readFieldOrNullReturnsDecodedValue() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        CompoundTag tag = PiSchemaSupport.tagOf(PiSchemaFieldCodecs.writeField(field, "boss"));

        assertEquals("boss", PiSchemaFieldCodecs.readFieldOrNull(tag, field, PiDecodeContext.strict()));
    }

    @Test
    void readFieldIntoReusesMutableCollectionTargets() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<List<String>> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "titles"), PiSyncScope.OWNER, true),
                PiSerializers.listOf(runtime.lookup(PiSerializers.STRING).orElseThrow())
        );

        CompoundTag tag = new CompoundTag();
        PiSchemaFieldCodecs.writeField(tag, field, List.of("boss", "core"));
        List<String> target = new ArrayList<>(List.of("stale"));

        assertSame(target, PiSchemaFieldCodecs.readFieldInto(tag, field, PiDecodeContext.strict(), target));
        assertEquals(List.of("boss", "core"), target);
    }

    @Test
    void readFieldOrNullReportsIssueAndReturnsNullWhenMissing() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals(null, PiSchemaFieldCodecs.readFieldOrNull(new CompoundTag(), field, context));
        assertEquals(1, context.result().issues().size());
        assertEquals(PiDecodeIssueCode.MISSING_FIELD_PAYLOAD, context.result().issues().get(0).code());
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
    void invalidFieldPayloadWithoutMessageUsesExceptionTypeFallback() {
        CompoundTag tag = new CompoundTag();
        tag.put("title", new CompoundTag());
        PiDecodeContext context = PiDecodeContext.strict();

        assertEquals("fallback", PiSchemaFieldCodecs.decode(tag, "title", blankDecodeMessageSerializer(), context, "fallback"));
        assertEquals(1, context.result().issues().size());
        assertEquals("title", context.result().issues().get(0).path());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("failed to decode field payload: IllegalStateException", context.result().issues().get(0).message());
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
    void missingFieldDoesNotAddNoiseWhenSamePathAlreadyFailed() {
        PiSerializeRuntime runtime = new PiSerializeRuntime();
        PiBuiltInSerializers.install(runtime);

        PiSchemaField<String> field = new PiSchemaField<>(
                new PiFieldDescriptor(new PiFieldKey(0, "title"), PiSyncScope.OWNER, true),
                runtime.lookup(PiSerializers.STRING).orElseThrow()
        );

        PiDecodeContext context = PiDecodeContext.strict();
        context.issue(PiDecodeIssueCode.SERIALIZER_FAILURE, "title", "already failed", true);

        assertEquals("fallback", PiSchemaFieldCodecs.readField(new CompoundTag(), field, context, "fallback"));
        assertEquals(1, context.result().issues().size());
        assertEquals(PiDecodeIssueCode.SERIALIZER_FAILURE, context.result().issues().get(0).code());
        assertEquals("title", context.result().issues().get(0).path());
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

    private static PiSerializer<String> blankDecodeMessageSerializer() {
        return new PiSerializer<>() {
            @Override
            public Codec<String> valueCodec() {
                return Codec.STRING;
            }

            @Override
            public PiNbtCodec<String> nbtCodec() {
                return new PiNbtCodec<>() {
                    @Override
                    public CompoundTag encode(String value) {
                        CompoundTag tag = new CompoundTag();
                        tag.putString("value", value);
                        return tag;
                    }

                    @Override
                    public String decode(CompoundTag tag) {
                        throw new IllegalStateException("   ");
                    }
                };
            }

            @Override
            public PiPacketCodec<String> packetCodec() {
                return new PiPacketCodec<>() {
                    @Override
                    public void write(net.minecraft.network.FriendlyByteBuf buffer, String value) {
                    }

                    @Override
                    public String read(net.minecraft.network.FriendlyByteBuf buffer, PiDecodeContext context) {
                        return "";
                    }
                };
            }
        };
    }
}
