package org.pickaid.piserializekit.runtime.schema;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiDecodeContext;
import org.pickaid.piserializekit.api.schema.PiDirtySet;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSchemaProvider;
import org.pickaid.piserializekit.api.schema.PiSchemaRegistry;
import org.pickaid.piserializekit.api.schema.PiStateBinding;
import org.pickaid.piserializekit.api.schema.PiSyncScope;
import org.pickaid.piserializekit.runtime.schema.support.PiSchemaSupport;

public final class TestSchemaProvider implements PiSchemaProvider {
    public static final PiFieldKey VALUE = new PiFieldKey(0, "value");
    public static final PiFieldDescriptor VALUE_FIELD = new PiFieldDescriptor(VALUE, PiSyncScope.CHUNK, true);
    public static final ResourceLocation SCHEMA_ID = new ResourceLocation("test", "schema_provider_state");
    public static final String SCHEMA_ID_STRING = SCHEMA_ID.toString();
    public static final int SCHEMA_VERSION = 1;

    public static final class TestState {
        public int value;
    }

    private static final PiStateBinding<TestState> BINDING = new PiStateBinding<>() {
        @Override
        public ResourceLocation schemaId() {
            return SCHEMA_ID;
        }

        @Override
        public int version() {
            return SCHEMA_VERSION;
        }

        @Override
        public Class<TestState> stateType() {
            return TestState.class;
        }

        @Override
        public TestState newState() {
            return new TestState();
        }

        @Override
        public List<PiFieldDescriptor> fields() {
            return List.of(VALUE_FIELD);
        }

        @Override
        public CompoundTag saveFull(TestState self) {
            return PiSchemaSupport.tagWithHeader(SCHEMA_ID_STRING, SCHEMA_VERSION, PiSchemaSupport.putInt("value", self.value));
        }

        @Override
        public void loadFull(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID_STRING, SCHEMA_VERSION)) {
                return;
            }
            self.value = PiSchemaSupport.getInt(tag, "value", context, self.value);
        }

        @Override
        public CompoundTag saveClientView(TestState self) {
            return saveFull(self);
        }

        @Override
        public CompoundTag writeDelta(TestState self, PiDirtySet dirtySet) {
            CompoundTag tag = PiSchemaSupport.headerTag(SCHEMA_ID_STRING, SCHEMA_VERSION);
            if (dirtySet.contains(VALUE)) {
                tag.put("value", PiSchemaSupport.putInt("value", self.value).getSecond());
            }
            return tag;
        }

        @Override
        public void applyDelta(TestState self, CompoundTag tag, PiDecodeContext context) {
            if (!PiSchemaSupport.validateHeader(tag, context, SCHEMA_ID_STRING, SCHEMA_VERSION)) {
                return;
            }
            if (tag.contains("value")) {
                self.value = PiSchemaSupport.getInt(tag, "value", context, self.value);
            }
        }
    };

    @Override
    public void register(PiSchemaRegistry registry) {
        registry.register(TestState.class, BINDING);
    }
}
