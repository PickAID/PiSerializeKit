package org.pickaid.piserializekit.api.packet;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.pickaid.piserializekit.api.schema.PiFieldKey;
import org.pickaid.piserializekit.api.schema.PiSchemaMigration;

/**
 * Generated runtime binding for one packet type.
 *
 * @param <T> packet type
 * @param <C> dispatch context type
 */
public interface PiPacketBinding<T, C extends PiPacketContext> {
    /**
     * Returns the stable packet id consumed by transport/runtime layers.
     */
    ResourceLocation packetId();

    /**
     * Returns the declared packet version.
     */
    int version();

    /**
     * Returns the generated transport direction.
     */
    PiPacketDirection direction();

    /**
     * Returns the authored packet class.
     */
    Class<T> packetType();

    /**
     * Returns the declared packet fields in encode/decode order.
     */
    List<PiFieldKey> fields();

    /**
     * Returns binding-local packet payload migrations in authored order.
     */
    default List<PiSchemaMigration> migrations() {
        return List.of();
    }

    /**
     * Returns the generated packet codec.
     */
    PiPacketCodec<T> codec();

    /**
     * Dispatches the decoded packet into its typed handler path.
     */
    void dispatch(T packet, C context);
}
