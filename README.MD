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
