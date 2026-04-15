package org.pickaid.piserializekit.runtime.packet.fixture;

import org.pickaid.piserializekit.api.packet.PiPacket;
import org.pickaid.piserializekit.api.packet.PiServerPacket;
import org.pickaid.piserializekit.api.schema.PiField;

@PiPacket
public final class ThrowingNoticePacket extends PiServerPacket {
    @PiField(serializer = ThrowingStringCodec.class)
    public String title;

    public ThrowingNoticePacket(String title) {
        this.title = title;
    }
}
