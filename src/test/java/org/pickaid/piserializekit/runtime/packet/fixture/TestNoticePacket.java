package org.pickaid.piserializekit.runtime.packet.fixture;

import java.util.List;
import org.pickaid.piserializekit.api.packet.PiPacket;
import org.pickaid.piserializekit.api.packet.PiServerPacket;
import org.pickaid.piserializekit.api.packet.PiServerPacketContext;
import org.pickaid.piserializekit.api.schema.PiField;
import org.pickaid.piserializekit.api.schema.PiSyncScope;

@PiPacket
public final class TestNoticePacket extends PiServerPacket {
    @PiField(id = "title", sync = PiSyncScope.OWNER, persist = false)
    public String title;

    @PiField(id = "lines", sync = PiSyncScope.OWNER, persist = false)
    public List<String> lines;

    public TestNoticePacket(String title, List<String> lines) {
        this.title = title;
        this.lines = lines;
    }

    @Override
    protected void handle(PiServerPacketContext context) {
    }
}
