package org.pickaid.piserializekit.api.schema;

import org.pickaid.piserializekit.api.service.PiSerializer;

public interface PiFieldCodecProvider<T> {
    PiSerializer<T> serializer();
}
