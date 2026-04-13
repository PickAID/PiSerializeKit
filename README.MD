# PiSerializeKit

[English](README_EN.md)

`PiSerializeKit` 是 Pi 系列的独立序列化基础库。

它负责统一 value codec、NBT codec 与 packet codec 的边界，让多个 Pi 仓库在“如何描述数据”这件事上使用同一套语言，而不是每个模组都重新定义一层。

它也应成为 `PiDataGraph` 对象计数、反应链状态与图节点数据的强类型序列化底座。

## Start Stage 目标

1. 提供稳定的 serializer、type key 与 service 查询接口；
2. 为 `Pibrary`、`PiNet`、`PiKubeJSCompat` 与未来 engine repos 提供共享序列化边界；
3. 保持独立仓库身份，不再塞回 `Pibrary` 单体结构；
4. 把 state schema 与 packet schema 收束到同一套字段语言上。

## 当前已提供

1. `PiSerializer`
   聚合 value codec、NBT codec、packet codec。
2. `PiNbtCodec`
   `CompoundTag` 编解码契约。
3. `PiPacketCodec`
   `FriendlyByteBuf` 编解码契约。
4. `PiSerializerType`
   稳定的 serializer 类型标识。
5. `PiSerializeService`
   注册与查询统一入口。
6. `PiSerializeServices`
   当前 runtime service 的安装与获取入口。
7. `@PiSyncModel` / `@PiField`
   编译期生成 state binding、projection、delta 与 schema migration 绑定。
8. `@PiPacket` / `@PiPacketNamespace` / `@PiPacketUpgrade`
   编译期生成 packet binding、packet provider、packet id、packet version 与 packet migration 链。
9. `PiSchemas` / `PiPackets`
   基于 `ServiceLoader` 的 schema / packet runtime registry，可按类型或 packet id 查询。
10. `PiDecodeContext`
    结构化 decode 诊断收集器，支持字段路径、fatal 标记与 migration 失败信息。
11. `PiRuntimeLookupException` / `PiRuntimeConflictException` / `PiRuntimeBootstrapException`
    统一的 runtime 异常模型，用于缺失绑定、冲突注册与 provider bootstrap 失败，并暴露最小 machine-readable 上下文。

## 作者侧硬约束

第一次写 `@PiSyncModel` / `@PiPacket` 前，当前需要明确这些规则：

1. `@PiSyncModel.version` 与 `@PiPacket.version` 必须 `>= 1`；
2. schema id 与 packet id 在同一轮编译内必须唯一，重复声明会编译期失败；
3. `@PiSyncModel`、`@PiPacket`、`@PiLivingService` 必须是 top-level concrete class；
4. `@PiSyncModel` 需要可访问的无参构造器，且该构造器不能声明 checked exception；
5. `@PiPacket` 用于生成 decode 的构造器必须和 `@PiField` 顺序匹配，且不能声明 checked exception；
6. `@PiField(serializer = ...)` provider 需要可访问的无参构造器，且不能声明 checked exception；
7. `@PiAfterDecode`、`@PiSchemaUpgrade`、`@PiPacketUpgrade` 方法不能声明 checked exception。

## 验证门槛

当前仓库把下面这条命令视为合并前的最低冷验证门槛：

```bash
bash ./gradlew clean test --no-daemon
```

仓库同时提供 `.github/workflows/ci.yml`，把同一条 `clean test` 变成固定 CI gate，而不是只依赖本地偶尔手跑。

## API 稳定边界

当前建议按下面三层理解兼容边界：

1. 稳定作者 API：
   `org.pickaid.piserializekit.api.schema`、`api.packet`、`api.service`、`api.runtime`，以及运行时入口 `PiSchemas`、`PiPackets`、`PiSerializeServices`。
2. 内部实现 API：
   `processor.*`、`processor.support.*`、`processor.model.*`、`runtime.*.support`、`runtime.*.codec` 等包默认不承诺稳定，不建议下游直接依赖。
3. 生成物命名边界：
   `_PiSchema`、`_PiFields`、`_PiPacket`、`_PiPacketProvider` 这类 generated type 主要是编译产物；下游更推荐通过 `PiSchemas` / `PiPackets` 或上层 pack 暴露的 host API 间接消费，而不是把这些名字当手写稳定契约。

## 当前明确不做满

本仓库当前仍然不负责：
1. channel 安装、发送目标、线程切换与 transport guard；
2. capability / host runtime 绑定与 gameplay service 生命周期；
3. 面向具体 UI / render / world runtime 的高层 author magic；
4. 反射式黑盒自动读写器；
5. 针对单个 engine 的重度运行时打包逻辑。

## 仓库定位

1. 这是独立基础仓库；
2. 它可被多个 Pi repo 直接消费；
3. 它负责“数据模型如何被描述、升级、诊断与绑定”；
4. `PiNet` 负责“这些 packet 如何被发送、路由与守卫”；
5. `Pibrary` 与后续 packs 负责“这些 schema / packet 如何进入具体 gameplay host”。
