# PiSerializeKit

[English](README_EN.md)

`PiSerializeKit` 是 Pi 系列里负责“数据怎么写、怎么读、怎么升级、怎么进包”的底层库。

如果你需要下面这些事，就会用到它：

1. 给一个状态类写持久化和同步结构；
2. 给一个 packet 写稳定 id、版本和 decode 逻辑；
3. 给某个类型提供统一 serializer；
4. 在运行时按 authored class 取回对应的 schema binding 或 packet binding。

大多数作者平时只会接触三层东西：

1. 注解：`@PiSyncModel`、`@PiField`、`@PiPacket`
2. 运行时入口：`PiSchemas`、`PiPackets`、`PiSerializeServices`
3. 少量 serializer API：`PiSerializer`、`PiSerializers`

你通常不需要手写或直接依赖 `_PiSchema`、`_PiPacket` 这类 generated class。

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

运行时你拿到的是 binding，而不是自己去找 generated class：

```java
PiStateBinding<ManaState> binding = PiSchemas.require(ManaState.class);

CompoundTag full = binding.saveFull(state);
binding.loadFull(restored, full, PiDecodeContext.strict());
```

如果你只想写默认 client view、persisted view 或 delta，也都走 binding：

```java
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

    @Override
    protected void handle(PiServerPacketContext context) {
    }
}
```

运行时同样按 authored class 或稳定 id 取 binding：

```java
PiPacketBinding<CastSkillPacket, ?> binding = PiPackets.require(CastSkillPacket.class);
FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

binding.codec().write(buf, new CastSkillPacket("fireball", 2));
CastSkillPacket decoded = binding.codec().read(buf);
```

### 3. 安装或临时覆盖 serializer runtime

如果你只是要 built-in serializer：

```java
PiSerializeRuntime runtime = new PiSerializeRuntime();
PiBuiltInSerializers.install(runtime);
PiSerializeServices.install(runtime);
```

如果你只是想在某个局部逻辑里临时切换 runtime：

```java
PiSerializeServices.withScope(runtime, () -> {
    PiSerializer<String> serializer = PiSerializeServices.requireSerializer(PiSerializers.STRING);
});
```

这个 scoped override 很适合测试、局部覆盖或上层 pack 做隔离策略。

## 你实际会依赖哪些 API

稳定的作者侧入口主要是这些：

1. `org.pickaid.piserializekit.api.schema.*`
2. `org.pickaid.piserializekit.api.packet.*`
3. `org.pickaid.piserializekit.api.service.*`
4. `org.pickaid.piserializekit.api.runtime.*`
5. `PiSchemas`
6. `PiPackets`
7. `PiSerializeServices`

下面这些默认按内部实现处理，不建议下游直接写死依赖：

1. `processor.*`
2. `processor.support.*`
3. `processor.model.*`
4. `runtime.*.support`
5. `runtime.*.codec`
6. generated class 名字本身

## 作者侧规则

第一次写 `@PiSyncModel` / `@PiPacket` 前，先记住这些硬规则：

1. `@PiSyncModel.version` 和 `@PiPacket.version` 必须 `>= 1`
2. schema id 和 packet id 在同一轮编译里必须唯一
3. `@PiSyncModel`、`@PiPacket`、`@PiLivingService` 必须是 top-level concrete class
4. `@PiSyncModel` 需要可访问的无参构造器，而且不能声明 checked exception
5. `@PiPacket` 的 decode 构造器必须和 `@PiField` 顺序一致，而且不能声明 checked exception
6. `@PiField(serializer = ...)` provider 需要可访问的无参构造器，而且不能声明 checked exception
7. `@PiAfterDecode`、`@PiSchemaUpgrade`、`@PiPacketUpgrade` 方法不能声明 checked exception

## 这个库不负责什么

这些事不属于 `PiSerializeKit`：

1. channel 安装、发送目标、线程切换、transport guard
2. capability / host runtime 绑定
3. gameplay service 生命周期
4. UI、render、world 的高层 author magic
5. 黑盒反射式自动读写

换句话说：

1. `PiSerializeKit` 负责数据如何描述、升级、诊断、绑定
2. `PiNet` 负责 packet 如何发送、路由、守卫
3. `Pibrary` 和后续 packs 负责这些 schema / packet 如何进入实际 gameplay host

## 验证

这个仓库当前把下面这条命令当作最低冷验证门槛：

```bash
bash ./gradlew clean test --no-daemon
```

仓库里也有 `.github/workflows/ci.yml`，会在 GitHub 上跑同一条 `clean test`。
