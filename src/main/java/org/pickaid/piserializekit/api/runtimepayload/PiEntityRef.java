package org.pickaid.piserializekit.api.runtimepayload;

import java.util.Optional;
import java.util.UUID;

public record PiEntityRef(Optional<Integer> entityId, Optional<UUID> uuid) {
    public PiEntityRef {
        entityId = entityId == null ? Optional.empty() : entityId;
        uuid = uuid == null ? Optional.empty() : uuid;
        if (entityId.isEmpty() && uuid.isEmpty()) {
            throw new IllegalArgumentException("entityId or uuid is required");
        }
    }

    public static PiEntityRef byId(int entityId) {
        return new PiEntityRef(Optional.of(entityId), Optional.empty());
    }

    public static PiEntityRef byUuid(UUID uuid) {
        return new PiEntityRef(Optional.empty(), Optional.of(uuid));
    }
}
