package org.pickaid.piserializekit.runtime.schema;

import java.util.Objects;
import org.pickaid.piserializekit.api.schema.PiFieldDescriptor;
import org.pickaid.piserializekit.api.service.PiSerializer;

/**
 * Runtime schema field descriptor bound to its serializer.
 *
 * @param <T> field value type
 */
public record PiSchemaField<T>(
        PiFieldDescriptor descriptor,
        PiSerializer<T> serializer
) {
    public PiSchemaField {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(serializer, "serializer");
    }

    public String key() {
        return descriptor.key().id();
    }
}
