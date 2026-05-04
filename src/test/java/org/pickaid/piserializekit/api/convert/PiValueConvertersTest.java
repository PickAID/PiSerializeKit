package org.pickaid.piserializekit.api.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.pickaid.piserializekit.api.service.PiSerializeService;
import org.pickaid.piserializekit.api.service.PiSerializer;
import org.pickaid.piserializekit.api.service.PiSerializers;
import org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers;
import org.pickaid.piserializekit.runtime.service.PiSerializeRuntime;

class PiValueConvertersTest {
    @Test
    void resourceLocationConverterAcceptsJsonTextAndCustomSources() {
        PiResourceLocationConverter converter = PiResourceLocationConverter.builder()
                .defaultNamespace("mna")
                .pathPrefix("components")
                .source(FakeBuilder.class, FakeBuilder::id)
                .build();

        assertEquals(id("mna", "components/fireball"),
                converter.convert("fireball", PiConversionContext.field("spell")));
        assertEquals(id("mna", "components/frost"),
                converter.convert(JsonParser.parseString("{\"name\":\"frost\"}"), PiConversionContext.field("spell")));
        assertEquals(id("custom", "components/arcane"),
                converter.convert(new FakeBuilder(id("custom", "arcane")), PiConversionContext.field("spell")));
    }

    @Test
    void typedResourceLocationConverterKeepsTypedInstancesAndParsesRawIds() {
        SpellId existing = new SpellId(id("mna", "existing"));
        PiValueConverter<SpellId> converter = PiTypedValueConverters.resourceLocationBacked(
                SpellId.class,
                SpellId::new,
                SpellId::location,
                PiResourceLocationConverter.builder().defaultNamespace("mna").build()
        );

        assertSame(existing, converter.convert(existing, PiConversionContext.field("spell")));
        assertEquals(new SpellId(id("mna", "fireball")),
                converter.convert("fireball", PiConversionContext.field("spell")));
    }

    @Test
    void stringIdConverterNormalizesEnumLikeValues() {
        PiStringIdConverter converter = PiStringIdConverter.builder().normalizeEnumName().build();

        assertEquals("fire_damage", converter.convert("Fire Damage", PiConversionContext.field("effect")));
        assertEquals("frost_bolt", converter.convert(JsonParser.parseString("{\"id\":\"Frost-Bolt\"}"), PiConversionContext.field("effect")));
        assertEquals("third_value", converter.convert(SampleEnum.THIRD_VALUE, PiConversionContext.field("effect")));
    }

    @Test
    void convertersReportTheFieldName() {
        PiResourceLocationConverter converter = PiResourceLocationConverter.builder().defaultNamespace("mna").build();

        PiConversionException error = assertThrows(PiConversionException.class,
                () -> converter.convert("", PiConversionContext.field("spell")));

        assertEquals("spell can't be empty", error.getMessage());
    }

    @Test
    void serializerFactoriesReuseBuiltInResourceLocationSerializer() {
        PiSerializeService service = new PiSerializeRuntime();
        PiBuiltInSerializers.install(service);

        PiSerializer<SpellId> serializer = PiSerializers.resourceLocationBacked(
                service,
                SpellId::new,
                SpellId::location
        );

        SpellId decoded = serializer.nbtCodec().decodeTag(StringTag.valueOf("mna:fireball"));

        assertEquals(new SpellId(id("mna", "fireball")), decoded);
        assertEquals(StringTag.valueOf("mna:fireball"), serializer.nbtCodec().encodeTag(decoded));
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private record SpellId(ResourceLocation location) {
    }

    private record FakeBuilder(ResourceLocation id) {
    }

    private enum SampleEnum {
        THIRD_VALUE
    }
}
