# PiSerializeKit 1.20.1 Wiki

PiSerializeKit 管数据结构本身：一个状态怎么保存、一个 packet 怎么编解码、旧版本 payload 怎么升级、复杂对象树怎么给出清楚的校验错误。

常用文档：

- [Schema 状态模型](schema.md)
- [Packet](packet.md)
- [Serializer runtime](runtime.md)
- [对象结构检查](inspection.md)
- [使用规则](rules.md)

## 日常入口

- `@PiSyncModel` + `@PiField`：给状态生成保存、加载、投影和 delta 逻辑。
- `@PiPacket`：给 packet 生成稳定 id、version 和 codec。
- `PiSchemas`：按类型保存/读取状态。
- `PiPackets`：按类型或 packet id 编解码 packet。
- `PiSerializeServices`：安装或临时切换 serializer runtime。
- `PiObjectInspector`：扫描 record、list、map、array 这类对象树。

一般代码不需要直接引用 generated class，也不需要从 `binding.codec()` 开始写。需要更细控制时，再下到 `PiStateBinding` 或 `PiPacketBinding`。
