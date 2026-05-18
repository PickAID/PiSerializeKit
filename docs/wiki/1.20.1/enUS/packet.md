# Packets

Mark packet classes with `@PiPacket`. The packet id can be explicit, or it can be inferred from a package namespace plus the class name.

```java
@PiPacketNamespace("example")
package com.example.packet;
```

```java
@PiPacket
public final class CastSkillPacket extends PiServerPacket {
    @PiField
    public final String skill;

    @PiField
    public final int level;

    public CastSkillPacket(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }
}
```

Most projects should not hand-write buffer registration. PiSerializeKit generates packet ids, versions, and codecs; PiNet wires those packets into the network handler and send API.

For debugging or low-level transport bridges, you can encode and decode directly:

```java
PiPacketBuffer buffer = PiPacketBuffers.heap();
CastSkillPacket packet = new CastSkillPacket("fireball", 2);

PiPackets.write(buffer, packet);

CastSkillPacket decoded = PiPackets.read(CastSkillPacket.class, buffer);
```

Use `@PiPacketUpgrade` for older packet versions. Legacy decode failures preserve structured decode issues so the error can point at the missing field, wrong type, or failing migration step.
