package org.pickaid.piserializekit.runtime.packet.fixture;

import org.pickaid.piserializekit.api.packet.PiPacket;
import org.pickaid.piserializekit.api.packet.PiServerPacket;
import org.pickaid.piserializekit.api.schema.PiField;

@PiPacket
public final class ThrowingCtorPacket extends PiServerPacket {
    @PiField
    public String title;

    public ThrowingCtorPacket(String title) {
        throw new IllegalStateException("ctor boom");
    }
}
