# PiSerializeKit

[English](README_EN.md)

`PiSerializeKit` 是 Pi 系列的独立序列化基础库。

它负责统一 value codec、NBT codec 与 packet codec 的边界，让多个 Pi 仓库在“如何描述数据”这件事上使用同一套语言，而不是每个模组都重新定义一层。

它也应成为 `PiDataGraph` 对象计数、反应链状态与图节点数据的强类型序列化底座。

## Start Stage 目标

1. 提供稳定的 serializer、type key 与 service 查询接口；
2. 为 `Pibrary`、`PiNet`、`PiKubeJSCompat` 与未来 engine repos 提供共享序列化边界；
3. 保持独立仓库身份，不再塞回 `Pibrary` 单体结构；
4. 先把契约建好，再决定自动推导、迁移和 schema 演进策略。

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

## 当前明确不做满

本阶段还不直接实现：
1. 自动 serializer 推导；
2. schema 迁移系统；
3. 版本兼容适配链；
4. 针对某个具体 engine 的重度序列化工具箱；
5. 反射式黑盒自动读写器。

## 仓库定位

1. 这是独立基础仓库；
2. 它可被多个 Pi repo 直接消费；
3. 它应优先保证数据边界清晰，而不是过早做复杂 runtime。
