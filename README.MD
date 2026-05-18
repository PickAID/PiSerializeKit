# PiSerializeKit

[English](README_EN.md)

`PiSerializeKit` 是 Pi 系列负责“数据怎么写、怎么读、怎么升级、怎么进包”的底层库。它提供 schema、packet、serializer、对象结构检查和 annotation processor guardrail。

当前主线版本文档：

- [1.20.1 中文 Wiki](docs/wiki/1.20.1/zhCN/README.md)
- [1.20.1 English Wiki](docs/wiki/1.20.1/enUS/README.md)

## 常用入口

- 注解：`@PiSyncModel`、`@PiField`、`@PiPacket`
- 运行时：`PiSchemas`、`PiPackets`、`PiSerializeServices`
- runtime payload：`PiRuntimePayload`、`PiRuntimePayloadBinding`
- serializer：`PiSerializer`、`PiSerializers`
- 对象结构检查：`PiObjectInspector`

平时不需要手写或直接依赖 generated class，也不应该把 `binding.codec()` 当作第一层日常用法。README 只保留入口；schema、packet、runtime、inspection 和 authoring rules 都在 versioned wiki 里维护。
