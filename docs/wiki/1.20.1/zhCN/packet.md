# Packet

packet 类用 `@PiPacket` 标记。packet id 可以显式写，也可以用 package 级 namespace 加类名推导。

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

多数项目不会直接手写 buffer 注册。PiSerializeKit 负责生成 packet id、version 和 codec；PiNet 负责把这些 packet 接到网络收发入口。

调试或桥接底层 transport 时，可以直接编解码：

```java
FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
CastSkillPacket packet = new CastSkillPacket("fireball", 2);

PiPackets.write(raw, packet);
raw.readerIndex(0);

CastSkillPacket decoded = PiPackets.read(CastSkillPacket.class, raw);
```

迁移旧 packet 时用 `@PiPacketUpgrade`。旧版 packet 解码失败会保留结构化 decode issue，方便定位是字段缺失、类型不对，还是 migration 本身失败。
