# PiSerializeKit

[English](README_EN.md)

`PiSerializeKit` 是 Pi 系列里负责“数据怎么写、怎么读、怎么升级、怎么进包”的底层库。

如果你需要下面这些事，就会用到它：

1. 给一个状态类写持久化和同步结构；
2. 给一个 packet 写稳定 id、版本和 decode 逻辑；
3. 给某个类型提供统一 serializer；
4. 在运行时直接按类型保存、读取状态或 packet。

平时常用的只有三层东西：

1. 注解：`@PiSyncModel`、`@PiField`、`@PiPacket`
2. 运行时入口：`PiSchemas`、`PiPackets`、`PiSerializeServices`
3. 少量 serializer API：`PiSerializer`、`PiSerializers`

你通常不需要手写或直接依赖 `_PiSchema`、`_PiPacket` 这类 generated class，也不该把 `binding.codec()` 当作第一层日常用法。

常见分层可以这样理解：

1. `api.*` 下面是你平时直接写代码会碰到的注解和契约
2. `PiSchemas`、`PiPackets` 是 runtime lookup 入口
3. `PiNet` 会直接消费这里生成出来的 packet id、version 和 codec

## 典型用法

### 1. 写一个可持久化、可同步的状态类

```java
@PiSyncModel(id = "example:mana_state", version = 1)
public final class ManaState {
    @PiField(id = "mana", sync = PiSyncScope.TRACKING, persist = true)
    public int mana;

    @PiField(id = "selected_spell", sync = PiSyncScope.OWNER, persist = true)
    public String selectedSpell = "";
}
```

最直接的用法是按类型直接保存和读取：

```java
CompoundTag full = PiSchemas.saveFull(state);
ManaState restored = PiSchemas.loadFull(ManaState.class, full);
```

如果你需要 client view、persisted view、delta 这种更细的控制，再下到 binding：

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag clientView = binding.saveClientView(state);
CompoundTag persisted = binding.savePersisted(state);
CompoundTag delta = binding.writeClientDelta(state, dirtySet);
```

### 2. 写一个 packet

先在包上提供 namespace，避免每个包都重复写：

```java
@PiPacketNamespace("example")
package com.example.packet;
```

然后写 packet 本体：

```java
@PiPacket
public final class CastSkillPacket extends PiServerPacket {
    @PiField(id = "skill", sync = PiSyncScope.OWNER, persist = false)
    public String skill;

    @PiField(id = "level", sync = PiSyncScope.OWNER, persist = false)
    public int level;

    public CastSkillPacket(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }
}
```

大多数时候，写完这个类就够了。`PiSerializeKit` 这边主要负责让这个 packet 有稳定 id、版本和 decode 逻辑，`PiNet` 会直接接住这层结果去做注册和收发。

这里的 `@PiField(sync = ...)` 只是为了让 packet 字段和 schema 字段复用同一套字段描述；route、tracking、broadcast 这些发送语义还是由上层网络层决定。

如果你在做调试、桥接或底层 transport 接线，也可以直接按类型编解码：

```java
CastSkillPacket packet = new CastSkillPacket("fireball", 2);
FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
PiPacketBuffer buffer = PiPacketBuffers.wrap(raw);

PiPackets.write(buffer, packet);
raw.readerIndex(0);

ResourceLocation id = packet.packetId();
int version = packet.version();
CastSkillPacket decoded = PiPackets.read(CastSkillPacket.class, buffer);
```

只有在你需要查 binding、字段列表、版本或 migration 信息时，才需要再下到 `PiPackets.require(...)`。

### 3. 安装或临时覆盖 serializer runtime

如果你只是要 built-in serializer：

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

这几个类的实际位置是：

1. `org.pickaid.piserializekit.runtime.service.PiSerializeRuntime`
2. `org.pickaid.piserializekit.runtime.service.PiBuiltInSerializers`
3. `org.pickaid.piserializekit.api.service.PiSerializeServices`

如果你只是想在某个局部逻辑里临时切换 runtime：

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer = PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

这个 scoped override 很适合测试、局部覆盖或上层 pack 做隔离策略。

## 更底层时再用这些 API

平时常用入口主要是这些：

1. `org.pickaid.piserializekit.api.schema.*`
2. `org.pickaid.piserializekit.api.packet.*`
3. `org.pickaid.piserializekit.api.service.*`
4. `org.pickaid.piserializekit.api.runtime.*`
5. `PiSchemas`
6. `PiPackets`
7. `PiSerializeServices`

如果你需要更多控制，再下到：

1. `PiStateBinding`
2. `PiPacketBinding`
3. `PiDecodeContext`
4. `PiSerializer`

这些更多是高级入口，不应该成为最先展示的日常写法。

## 使用规则

第一次写 `@PiSyncModel` / `@PiPacket` 前，先记住这些硬规则：

1. `@PiSyncModel.version` 和 `@PiPacket.version` 必须 `>= 1`
2. schema id 和 packet id 在同一轮编译里必须唯一
3. `@PiSyncModel`、`@PiPacket` 必须是 top-level concrete class
4. `@PiSyncModel` 需要可访问的无参构造器，而且不能声明 checked exception
5. `@PiPacket` 的 decode 构造器必须和 `@PiField` 顺序一致，而且不能声明 checked exception
6. `@PiField(serializer = ...)` provider 需要可访问的无参构造器，而且不能声明 checked exception
7. `@PiAfterDecode`、`@PiSchemaUpgrade`、`@PiPacketUpgrade` 方法不能声明 checked exception
