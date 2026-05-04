package org.pickaid.piserializekit.api.convert;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class PiTypedValueConverters {
    private PiTypedValueConverters() {
    }

    public static <T> PiValueConverter<T> resourceLocationBacked(
            Class<T> targetType,
            Function<ResourceLocation, T> factory,
            Function<T, ResourceLocation> extractor,
            PiResourceLocationConverter idConverter
    ) {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(idConverter, "idConverter");
        return (value, context) -> {
            Object unwrapped = context.unwrap(value);
            if (targetType.isInstance(unwrapped)) {
                return targetType.cast(unwrapped);
            }
            return factory.apply(idConverter.convert(unwrapped, context));
        };
    }

    public static <T> PiValueConverter<T> stringBacked(
            Class<T> targetType,
            Function<String, T> factory,
            Function<T, String> extractor,
            PiStringIdConverter idConverter
    ) {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(idConverter, "idConverter");
        return (value, context) -> {
            Object unwrapped = context.unwrap(value);
            if (targetType.isInstance(unwrapped)) {
                return targetType.cast(unwrapped);
            }
            return factory.apply(idConverter.convert(unwrapped, context));
        };
    }
}
